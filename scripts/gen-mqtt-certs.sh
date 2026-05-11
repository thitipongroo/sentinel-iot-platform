#!/usr/bin/env bash
# Generates a self-signed CA, Mosquitto server certificate, and optionally
# client certificates for mTLS (mutual TLS) authentication.
#
# Usage:
#   bash scripts/gen-mqtt-certs.sh [hostname] [--with-client-certs]
#
# Arguments:
#   hostname            MQTT broker hostname / IP (default: localhost)
#                       Must match the hostname clients use to connect.
#                       Example: bash scripts/gen-mqtt-certs.sh mqtt.example.com
#
#   --with-client-certs Also generate client certificates for the backend service
#                       and a default device. Required when MQTT_MTLS_ENABLED=true.
#                       Example: bash scripts/gen-mqtt-certs.sh localhost --with-client-certs
#
# After running, restart the stack to activate TLS on :8883:
#   docker compose restart mosquitto
#
# For production use a proper CA (Let's Encrypt, internal PKI, or AWS ACM).
# Self-signed certs are suitable for dev / portfolio demonstration only.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CERTS_DIR="$SCRIPT_DIR/../mosquitto/certs"
HOSTNAME="localhost"
WITH_CLIENT_CERTS="false"

# ── Parse arguments ──────────────────────────────────────────────────────────
for arg in "$@"; do
  case "$arg" in
    --with-client-certs) WITH_CLIENT_CERTS="true" ;;
    --*)
      echo "Unknown option: $arg" >&2
      exit 1
      ;;
    *) HOSTNAME="$arg" ;;
  esac
done

mkdir -p "$CERTS_DIR"
echo "[certs] Generating for hostname: $HOSTNAME"
echo "[certs] Output directory: $CERTS_DIR"
echo "[certs] Client certs: $WITH_CLIENT_CERTS"

# ── 1. CA private key + self-signed certificate ──────────────────────────────
echo ""
echo "[certs] Step 1/3 — Generating CA..."
openssl genrsa -out "$CERTS_DIR/ca.key" 4096

openssl req -new -x509 -days 3650 \
  -key "$CERTS_DIR/ca.key" \
  -out "$CERTS_DIR/ca.crt" \
  -subj "/CN=Sentinel IoT CA/O=Sentinel/C=TH"

# ── 2. Server private key + CSR ──────────────────────────────────────────────
echo "[certs] Step 2/3 — Generating server certificate..."
openssl genrsa -out "$CERTS_DIR/server.key" 4096

openssl req -new \
  -key "$CERTS_DIR/server.key" \
  -out "$CERTS_DIR/server.csr" \
  -subj "/CN=$HOSTNAME/O=Sentinel/C=TH"

# Sign server cert with CA (SAN covers localhost + hostname + mosquitto container DNS)
openssl x509 -req -days 3650 \
  -in  "$CERTS_DIR/server.csr" \
  -CA  "$CERTS_DIR/ca.crt" \
  -CAkey "$CERTS_DIR/ca.key" \
  -CAcreateserial \
  -out "$CERTS_DIR/server.crt" \
  -extfile <(printf "subjectAltName=DNS:localhost,DNS:mosquitto,DNS:%s,IP:127.0.0.1" "$HOSTNAME")

rm -f "$CERTS_DIR/server.csr" "$CERTS_DIR/ca.srl"

# ── 3. Client certificates (mTLS) ────────────────────────────────────────────
# Required only when MQTT_MTLS_ENABLED=true. Each connecting service and device
# must present a certificate signed by this CA. The broker verifies the client cert
# against ca.crt before allowing the connection (require_certificate=true).
#
# Generated clients:
#   backend  — Spring Boot backend service (MqttConsumerService)
#   device   — Default device client cert; production devices need individual certs.
echo "[certs] Step 3/3 — $([ "$WITH_CLIENT_CERTS" = "true" ] && echo "Generating client certificates..." || echo "Skipping client certificates (use --with-client-certs to generate)")"

if [ "$WITH_CLIENT_CERTS" = "true" ]; then
  for CLIENT in backend device; do
    echo "[certs]   Generating client cert: sentinel-$CLIENT"

    openssl genrsa -out "$CERTS_DIR/client-$CLIENT.key" 2048

    openssl req -new \
      -key "$CERTS_DIR/client-$CLIENT.key" \
      -out "$CERTS_DIR/client-$CLIENT.csr" \
      -subj "/CN=sentinel-$CLIENT/O=Sentinel/C=TH"

    openssl x509 -req -days 3650 \
      -in  "$CERTS_DIR/client-$CLIENT.csr" \
      -CA  "$CERTS_DIR/ca.crt" \
      -CAkey "$CERTS_DIR/ca.key" \
      -CAcreateserial \
      -out "$CERTS_DIR/client-$CLIENT.crt"

    rm -f "$CERTS_DIR/client-$CLIENT.csr" "$CERTS_DIR/ca.srl"
    chmod 600 "$CERTS_DIR/client-$CLIENT.key"
    echo "[certs]   Written: client-$CLIENT.crt + client-$CLIENT.key"
  done
fi

chmod 600 "$CERTS_DIR/ca.key" "$CERTS_DIR/server.key"

echo ""
echo "[certs] Done. Files written to $CERTS_DIR:"
echo "  ca.crt            — CA certificate — distribute to all MQTT clients"
echo "  server.crt        — Mosquitto server certificate"
echo "  server.key        — Mosquitto server private key (excluded from git)"
if [ "$WITH_CLIENT_CERTS" = "true" ]; then
echo "  client-backend.*  — Backend service client cert (for mTLS)"
echo "  client-device.*   — Default device client cert (for mTLS)"
echo ""
echo "To mount client certs into the backend container, add to docker-compose.yml:"
echo "  volumes:"
echo "    - ./mosquitto/certs/client-backend.crt:/app/certs/client.crt:ro"
echo "    - ./mosquitto/certs/client-backend.key:/app/certs/client.key:ro"
echo "    - ./mosquitto/certs/ca.crt:/app/certs/ca.crt:ro"
fi
echo ""
echo "Restart Mosquitto to activate TLS on :8883:"
echo "  docker compose restart mosquitto"
echo ""
echo "Connect with TLS (test):"
echo "  mosquitto_pub --cafile $CERTS_DIR/ca.crt -h $HOSTNAME -p 8883 -t test -m hello -u sentinel-device -P <password>"
if [ "$WITH_CLIENT_CERTS" = "true" ]; then
echo ""
echo "Connect with mTLS (test) — set MQTT_MTLS_ENABLED=true first:"
echo "  mosquitto_pub --cafile $CERTS_DIR/ca.crt \\"
echo "    --cert $CERTS_DIR/client-device.crt \\"
echo "    --key $CERTS_DIR/client-device.key \\"
echo "    -h $HOSTNAME -p 8883 -t test -m hello"
fi
