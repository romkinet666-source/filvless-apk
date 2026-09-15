"""Filvless device gateway. Panel credentials never leave this process.

A subscription's high-entropy short UUID is a bearer credential, just as its
subscription URL is. Requests never accept a panel user ID or an upstream URL.
"""
import hashlib
import hmac
import json
import logging
import os
import re
import threading
import time
import urllib.error
import urllib.request
from http import HTTPStatus

VERSION = "0.6.0"
TOKEN = re.compile(r"[A-Za-z0-9_-]{16,64}\Z")
DEVICE = re.compile(r"[0-9a-f]{64}\Z")
LOG = logging.getLogger("filvless-api")


class ApiError(Exception):
    def __init__(self, status, code):
        self.status, self.code = status, code


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Panel:
    def __init__(self, base, token, device_token=None):
        if base != "https://panel.fillvl.ru" or not token:
            raise ValueError("A configured HTTPS panel and API token are required")
        self.base, self.token = base, token
        self.device_token = device_token or token
        self.opener = urllib.request.build_opener(NoRedirect())

    def call(self, path, body=None):
        request = urllib.request.Request(self.base + "/api" + path,
            data=None if body is None else json.dumps(body).encode(),
            headers={"Authorization": "Bearer " + (self.device_token if path.startswith("/hwid/") else self.token), "Accept": "application/json",
                     "Content-Type": "application/json"})
        try:
            with self.opener.open(request, timeout=10) as response:
                raw = response.read(1_048_577)
            if len(raw) > 1_048_576:
                raise ApiError(502, "panel_unavailable")
            result = json.loads(raw)["response"]
            if not isinstance(result, dict):
                raise ValueError()
            return result
        except urllib.error.HTTPError as error:
            if error.code in (401, 403):
                raise ApiError(503, "panel_permissions") from None
            if error.code == 404:
                raise ApiError(404, "not_found") from None
            raise ApiError(502, "panel_unavailable") from None
        except (OSError, ValueError, KeyError):
            raise ApiError(502, "panel_unavailable") from None


class Limiter:
    """Bounded process-local fixed windows; deploy with one threaded worker."""
    def __init__(self, clock=time.monotonic):
        self.clock, self.lock, self.buckets = clock, threading.Lock(), {}

    def take(self, key, limit, seconds=60):
        now = self.clock()
        with self.lock:
            if len(self.buckets) >= 10000:
                self.buckets = {k: v for k, v in self.buckets.items() if v[0] > now}
                if key not in self.buckets and len(self.buckets) >= 10000:
                    raise ApiError(429, "rate_limited")
            end, count = self.buckets.get(key, (now + seconds, 0))
            if end <= now:
                end, count = now + seconds, 0
            if count >= limit:
                raise ApiError(429, "rate_limited")
            self.buckets[key] = end, count + 1


class Gateway:
    def __init__(self, panel, secret, limiter=None):
        if len(secret) < 32:
            raise ValueError("A persistent random signing secret is required")
        self.panel, self.secret = panel, secret.encode()
        self.limiter = limiter or Limiter()

    def device_id(self, user_id, hwid):
        return hmac.new(self.secret, f"{user_id}:{hwid}".encode(), hashlib.sha256).hexdigest()

    def user(self, credential):
        try:
            user = self.panel.call("/users/by-short-uuid/" + credential)
        except ApiError as error:
            if error.status == 404:
                raise ApiError(401, "invalid_subscription") from None
            raise
        if (type(user.get("id")) is not int or user["id"] <= 0 or
                not hmac.compare_digest(str(user.get("shortUuid", "")), credential) or
                user.get("status") not in ("ACTIVE", "EXPIRED", "LIMITED")):
            raise ApiError(401, "invalid_subscription")
        return user

    def devices(self, user):
        data = self.panel.call("/hwid/devices/" + str(user["id"]))
        rows = data.get("devices")
        if not isinstance(rows, list) or len(rows) > 1000:
            raise ApiError(502, "panel_unavailable")
        for row in rows:
            if (not isinstance(row, dict) or row.get("userId") != user["id"] or
                    not isinstance(row.get("hwid"), str) or not row["hwid"]):
                raise ApiError(502, "panel_unavailable")
        return rows

    def snapshot(self, user, rows, current):
        def text(row, key):
            return str(row.get(key) or "")[:160]
        limit = user.get("hwidDeviceLimit")
        return {"total": len(rows), "limit": limit if type(limit) is int and limit > 0 else None,
                "devices": [{"id": self.device_id(user["id"], row["hwid"]),
                    "model": text(row, "deviceModel"), "platform": text(row, "platform"),
                    "osVersion": text(row, "osVersion"), "createdAt": text(row, "createdAt"),
                    "updatedAt": text(row, "updatedAt"),
                    "isCurrent": bool(current) and hmac.compare_digest(row["hwid"].encode(), current.encode())} for row in rows]}

    def dispatch(self, env):
        path, method = env.get("PATH_INFO", ""), env.get("REQUEST_METHOD", "")
        if path == "/health" and method == "GET":
            return {"status": "ok", "version": VERSION}
        if (path, method) not in (("/v1/devices", "GET"), ("/v1/devices/delete", "POST")):
            raise ApiError(404, "not_found")
        # Caddy overwrites X-Real-IP; the application listens only on loopback.
        ip = env.get("HTTP_X_REAL_IP") or env.get("REMOTE_ADDR", "")
        self.limiter.take("global", 1200)
        self.limiter.take("ip:" + ip[:64], 60)
        auth = env.get("HTTP_AUTHORIZATION", "")
        credential = auth[7:] if auth.startswith("Bearer ") else ""
        if not TOKEN.fullmatch(credential):
            raise ApiError(401, "invalid_subscription")
        digest = hashlib.sha256(credential.encode()).hexdigest()
        self.limiter.take("sub:" + digest, 30)
        target = None
        if method == "POST":
            self.limiter.take("delete:" + digest, 3)
            if env.get("CONTENT_TYPE", "").split(";")[0].strip() != "application/json":
                raise ApiError(400, "invalid_request")
            try:
                length = int(env.get("CONTENT_LENGTH", "0"))
                if not 1 <= length <= 512:
                    raise ValueError()
                def unique(pairs):
                    result = {}
                    for key, value in pairs:
                        if key in result:
                            raise ValueError()
                        result[key] = value
                    return result
                data = json.loads(env["wsgi.input"].read(length), object_pairs_hook=unique)
                if not isinstance(data, dict) or set(data) != {"deviceId"}:
                    raise ValueError()
                target = data["deviceId"]
                if not isinstance(target, str) or not DEVICE.fullmatch(target):
                    raise ValueError()
            except (ValueError, UnicodeError, KeyError):
                raise ApiError(400, "invalid_request") from None
        user = self.user(credential)
        rows = self.devices(user)
        if target:
            row = next((row for row in rows if hmac.compare_digest(
                self.device_id(user["id"], row["hwid"]), target)), None)
            if row is None:
                raise ApiError(404, "device_not_found")
            # Both fields come from the authenticated user's fresh panel response.
            self.panel.call("/hwid/devices/delete", {"userId": user["id"], "hwid": row["hwid"]})
            # Exclude the deleted record locally; GET refreshes the panel on the next visit.
            rows = [item for item in rows if item["hwid"] != row["hwid"]]
        current = env.get("HTTP_X_DEVICE_ID", "")[:128]
        return self.snapshot(user, rows, current)

    def __call__(self, env, start_response):
        status = 200
        try:
            data = self.dispatch(env)
        except ApiError as error:
            status, data = error.status, {"error": error.code}
        except Exception:
            # Never log request bodies, credentials, URLs or upstream responses.
            LOG.error("Request failed with an unexpected internal error")
            status, data = 500, {"error": "internal_error"}
        body = json.dumps(data, ensure_ascii=False).encode()
        headers = [("Content-Type", "application/json; charset=utf-8"),
                   ("Content-Length", str(len(body))), ("Cache-Control", "no-store"),
                   ("X-Content-Type-Options", "nosniff")]
        if status == 429:
            headers.append(("Retry-After", "60"))
        start_response(f"{status} {HTTPStatus(status).phrase}", headers)
        return [body]


def create_app():
    return Gateway(Panel(os.environ["PANEL_URL"], os.environ["PANEL_TOKEN"],
                         os.environ.get("PANEL_DEVICE_TOKEN")), os.environ["DEVICE_ID_SECRET"])
