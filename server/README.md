# Filvless device API

Small WSGI gateway for Remnawave v3 (numeric user IDs). Public origin:
`https://apkapi.flynode.ru`. Runs behind Caddy on loopback port 8765.

## Authentication and boundaries

`Authorization: Bearer <subscription-short-uuid>` uses the secret from the
user's subscription URL. Anyone holding that subscription link can manage its
registered devices; treat the link as a password. This is not Telegram account
authentication. The server resolves the owner on every request. It never accepts
a client-provided user ID, panel URL or raw device HWID for deletion.

- `GET /health`: process health and gateway version (does not probe the panel).
- `GET /v1/devices`: own registered devices and explicit positive device limit.
  A null limit means the panel did not provide an explicit positive limit;
  it does not imply unlimited devices.
- `POST /v1/devices/delete`, JSON `{ "deviceId": "<opaque-id-from-list>" }`:
  re-fetches the owner and owned devices, deletes the matching record only.
- Optional `X-Device-Id`: current app HWID, used solely for the current-device badge.

Panel token scopes: `users` read by short UUID, `hwid-user-devices` list by user,
and delete one HWID device. The panel token and HMAC secret live only in
`/etc/filvless-api/environment`, root-owned mode 600. No body/header access logging.
No device database, no cross-user enumeration, no browser CORS access, no redirects
when calling the panel. In-memory rate limits require the configured single
Gunicorn worker; do not increase workers without shared rate limiting.

Device records represent subscription registration, not live VPN connections.
Deleting a record does not revoke previously downloaded proxy credentials; a
device holding the subscription link can register again. Credential revocation
and device bans are not implemented by this endpoint.

## Deploy

Install Caddy and Python venv support. Create system user `filvless-api`.
Copy this directory to a versioned folder under `/opt/filvless-api/releases/`,
point `/opt/filvless-api/current` to it, and create the venv at
`/opt/filvless-api/venv`. Install `requirements.txt` inside that venv.

Root-only environment file (never commit real values):

```text
PANEL_URL=https://panel.fillvl.ru
PANEL_TOKEN=<restricted-panel-api-token>
DEVICE_ID_SECRET=<persistent-random-secret-at-least-32-characters>
```

Install `filvless-api.service`, validate `Caddyfile` with `caddy validate`, enable
the API and Caddy services. Caddy obtains and renews TLS automatically. Port 8765
must remain bound to 127.0.0.1; Caddy overwrites `X-Real-IP` used by rate limiting.
Keep at least one previous release. Roll back by switching `current` to it and
restarting only `filvless-api`. First-install removal: stop/disable `filvless-api`
and Caddy, leaving the existing monitoring services untouched.

Run tests: `python -m unittest discover -s server -v` from the repository root.
Production tests must be read-only; do not delete customer devices as a smoke test.
