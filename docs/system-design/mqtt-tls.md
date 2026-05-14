# MQTT TLS / mTLS — Sentinel IoT Platform

By default the broker listens on port **1883 (plain TCP)** — suitable for local development and demo. For production deployments where devices communicate over the internet, enable TLS on port **8883**.

---

## Generate Certificates

```bash
# TLS only — encrypts traffic, clients verify the server cert
bash scripts/gen-mqtt-certs.sh mqtt.yourdomain.com

# mTLS — server AND every client must present a certificate
bash scripts/gen-mqtt-certs.sh mqtt.yourdomain.com --with-client-certs
```

Certificates are written to `mosquitto/certs/`:

| File | Purpose |
|------|---------|
| `ca.crt` | CA certificate — distribute to all MQTT clients |
| `server.crt` / `server.key` | Mosquitto server certificate |
| `client-backend.crt` / `.key` | Backend service client cert (mTLS only) |
| `client-device.crt` / `.key` | Default device client cert (mTLS only) |

---

## Activate TLS

```bash
docker compose restart mosquitto
```

Set env vars to harden the broker:

| Env var | Effect |
|---------|--------|
| `MQTT_TLS_REQUIRED=true` | Disables plaintext port 1883 |
| `MQTT_MTLS_ENABLED=true` | Requires client certificates on every connection |

---

## Test the Connection

```bash
# TLS
mosquitto_pub --cafile mosquitto/certs/ca.crt \
  -h mqtt.yourdomain.com -p 8883 \
  -t test -m hello \
  -u sentinel-device -P <password>

# mTLS
mosquitto_pub --cafile mosquitto/certs/ca.crt \
  --cert mosquitto/certs/client-device.crt \
  --key  mosquitto/certs/client-device.key \
  -h mqtt.yourdomain.com -p 8883 \
  -t test -m hello
```

> Self-signed certificates are suitable for dev and portfolio demonstration only. For production use a proper CA (Let's Encrypt, internal PKI, or AWS ACM PCA).
