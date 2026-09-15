import copy
import io
import json
import unittest
from app import ApiError, Gateway, Limiter

CREDENTIAL = "test-subscription-0001"


class FakePanel:
    def __init__(self):
        self.calls = []
        self.user = {"id": 42, "shortUuid": CREDENTIAL, "status": "ACTIVE", "hwidDeviceLimit": 3,
                     "vlessUuid": "never-expose", "telegramId": 12345}
        self.rows = [{"userId": 42, "hwid": "device-a", "deviceModel": "Pixel", "platform": "Android",
                      "requestIp": "192.0.2.1", "userAgent": "private-agent"}]
        self.error = None

    def call(self, path, body=None):
        self.calls.append((path, body))
        if self.error:
            raise self.error
        if path.startswith("/users/by-short-uuid/"):
            return copy.deepcopy(self.user)
        if path == "/hwid/devices/delete":
            self.rows = [r for r in self.rows if r["hwid"] != body["hwid"]]
        return {"devices": copy.deepcopy(self.rows), "total": len(self.rows)}


class GatewayTests(unittest.TestCase):
    def setUp(self):
        self.panel = FakePanel()
        self.app = Gateway(self.panel, "x" * 32)

    def request(self, method="GET", path="/v1/devices", credential=CREDENTIAL, body=None, raw=None):
        raw = raw if raw is not None else json.dumps(body).encode() if body is not None else b""
        env = {"REQUEST_METHOD": method, "PATH_INFO": path, "HTTP_AUTHORIZATION": "Bearer " + credential,
               "REMOTE_ADDR": "127.0.0.1", "HTTP_X_DEVICE_ID": "device-a", "CONTENT_TYPE": "application/json",
               "CONTENT_LENGTH": str(len(raw)), "wsgi.input": io.BytesIO(raw)}
        response = []
        payload = b"".join(self.app(env, lambda status, headers: response.append((int(status[:3]), dict(headers)))))
        return response[0][0], json.loads(payload), response[0][1]

    def test_health_does_not_query_panel(self):
        self.assertEqual(200, self.request(path="/health", credential="")[0])
        self.assertEqual([], self.panel.calls)

    def test_snapshot_only_exposes_device_metadata(self):
        status, data, headers = self.request()
        self.assertEqual((status, data["total"], data["limit"]), (200, 1, 3))
        self.assertTrue(data["devices"][0]["isCurrent"])
        self.assertEqual("no-store", headers["Cache-Control"])
        for secret in ["device-a", "never-expose", "192.0.2.1", "private-agent", "telegramId", "shortUuid"]:
            self.assertNotIn(secret, json.dumps(data))

    def test_missing_and_malformed_auth_never_reach_panel(self):
        for credential in ["", "short", "https://example.com/credential", "../" + CREDENTIAL, "a" * 65]:
            self.assertEqual(401, self.request(credential=credential)[0])
        self.assertEqual([], self.panel.calls)

    def test_mismatched_user_rejected(self):
        self.panel.user["shortUuid"] = "another-subscription"
        self.assertEqual(401, self.request()[0])
        self.assertEqual(1, len(self.panel.calls))

    def test_disabled_user_rejected(self):
        self.panel.user["status"] = "DISABLED"
        self.assertEqual(401, self.request()[0])

    def test_unknown_user_is_unauthorized(self):
        self.panel.error = ApiError(404, "not_found")
        self.assertEqual(401, self.request()[0])

    def test_panel_permissions_have_actionable_error(self):
        self.panel.error = ApiError(503, "panel_permissions")
        self.assertEqual((503, {"error": "panel_permissions"}), self.request()[:2])

    def test_foreign_device_in_upstream_response_fails_closed(self):
        self.panel.rows[0]["userId"] = 99
        self.assertEqual(502, self.request()[0])

    def test_delete_uses_authenticated_user_and_fresh_owned_device(self):
        device = self.app.device_id(42, "device-a")
        status, data, _ = self.request("POST", "/v1/devices/delete", body={"deviceId": device})
        self.assertEqual((status, data["total"]), (200, 0))
        self.assertEqual(("/hwid/devices/delete", {"userId": 42, "hwid": "device-a"}), self.panel.calls[-1])

    def test_foreign_device_cannot_be_deleted(self):
        device = self.app.device_id(99, "device-a")
        self.assertEqual(404, self.request("POST", "/v1/devices/delete", body={"deviceId": device})[0])
        self.assertFalse(any(body for _, body in self.panel.calls))

    def test_injected_user_id_rejected_before_panel(self):
        self.assertEqual(400, self.request("POST", "/v1/devices/delete",
            body={"deviceId": "a" * 64, "userId": 99})[0])
        self.assertEqual([], self.panel.calls)

    def test_duplicate_json_fields_rejected(self):
        self.assertEqual(400, self.request("POST", "/v1/devices/delete", raw=b'{"deviceId":"a","deviceId":"b"}')[0])

    def test_invalid_json_and_oversize_body_rejected(self):
        for body in [b"{", b"[]", b"x" * 513]:
            self.assertEqual(400, self.request("POST", "/v1/devices/delete", raw=body)[0])

    def test_delete_requires_post_and_specific_endpoint(self):
        self.assertEqual(404, self.request("GET", "/v1/devices/delete")[0])
        self.assertEqual(404, self.request("POST", "/api/hwid/devices/delete-all", body={})[0])

    def test_rate_limiter_expires_and_bounds_requests(self):
        now = [0]
        limiter = Limiter(lambda: now[0])
        limiter.take("test", 1)
        with self.assertRaises(ApiError) as error:
            limiter.take("test", 1)
        self.assertEqual(429, error.exception.status)
        now[0] = 61
        limiter.take("test", 1)

    def test_deleted_device_is_not_deleted_twice(self):
        body = {"deviceId": self.app.device_id(42, "device-a")}
        self.assertEqual(200, self.request("POST", "/v1/devices/delete", body=body)[0])
        self.assertEqual(404, self.request("POST", "/v1/devices/delete", body=body)[0])
        self.assertEqual(1, sum(body is not None for _, body in self.panel.calls))


if __name__ == "__main__":
    unittest.main()
