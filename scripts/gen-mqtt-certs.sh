#!/usr/bin/env bash
# Generates a self-signed CA and Mosquitto server certificate for TLS on port 8883.
#
# Usage:
#   bash scripts/gen-mqtt-certs.sh [hostname]
#
# The hostname defaults to 'localhost'. For a remote server, pass the public
# hostname or IP so the SAN matches what clients connect to:
#   bash scripts/gen-mqtt-certs.sh mqtt.example.com
#
# After running, restart the stack to activate TLS:
#   docker compose restart mosquitto
#
# Certs are valid for 10 years (dev/portfolio). For production, use a proper
# CA (Let's Encrypt via Certbot, internal PKI, or AWS ACM).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CERTS_DIR="$SCRIPT_DIR/../mosquitto/certs"
HOSTNAME="${1:-localhost}"

mkdir -p "$CERTS_DIR"

echo "[certs] Generating for hostname: $HOSTNAME"
echo "[certs] Output: $CERTS_DIR"

# ── 1. CA private key + self-signed certificate ─────────────────────────────
openssl genrsa -out "$CERTS_DIR/ca.key" 4096

openssl req -new -x509 -days 3650 \
  -key "$CERTS_DIR/ca.key" \
  -out "$CERTS_DIR/ca.crt" \
  -subj "/CN=Sentinel IoT CA/O=Sentinel/C=TH"

# ── 2. Server private key + CSR ──────────────────────────────────────────────
openssl genrsa -out "$CERTS_DIR/server.key" 4096

openssl req -new \
  -key "$CERTS_DIR/server.key" \
  -out "$CERTS_DIR/server.csr" \
  -subj "/CN=$HOSTNAME/O=Sentinel/C=TH"

# ── 3. Sign server cert with CA (SAN covers localhost + hostname + mosquitto) ─
openssl x509 -req -days 3650 \
  -in  "$CERTS_DIR/server.csr" \
  -CA  "$CERTS_DIR/ca.crt" \
  -CAkey "$CERTS_DIR/ca.key" \
  -CAcreateserial \
  -out "$CERTS_DIR/server.crt" \
  -extfile <(printf "subjectAltName=DNS:localhost,DNS:mosquitto,DNS:%s,IP:127.0.0.1" "$HOSTNAME")

rm -f "$CERTS_DIR/server.csr" "$CERTS_DIR/ca.srl"
chmod 600 "$CERTS_DIR/ca.key" "$CERTS_DIR/server.key"

echo ""
echo "[certs] Done. Files written to $CERTS_DIR:"
echo "  ca.crt     — CA certificate — distribute to MQTT clients for trust verification"
echo "  server.crt — Mosquitto server certificate"
echo "  server.key — Mosquitto server private key (keep secret, excluded from git)"
echo ""
echo "Restart Mosquitto to activate TLS on :8883:"
echo "  docker compose restart mosquitto"
echo ""
echo "MQTT clients must trust ca.crt. For mosquitto_pub / mosquitto_sub:"
echo "  mosquitto_pub --cafile mosquitto/certs/ca.crt -h $HOSTNAME -p 8883 ..."
