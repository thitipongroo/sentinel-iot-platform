#!/bin/sh
# Runs inside the eclipse-mosquitto container before the broker starts.
# 1. Provisions the password file from environment variables (overwritten on every start
#    so credentials stay in sync with the environment — no stale passwords from a prior run).
# 2. Auto-appends a TLS listener block if certs are present in /mosquitto/certs/.
#    Run scripts/gen-mqtt-certs.sh to generate self-signed certs and restart.
set -eu

PASSWD_FILE="/mosquitto/config/passwd"
CERTS_DIR="/mosquitto/certs"
BASE_CONFIG="/mosquitto/config/mosquitto.conf"
RUNTIME_CONFIG="/tmp/mosquitto-runtime.conf"

echo "[entrypoint] Provisioning MQTT password file..."

mosquitto_passwd -b -c "$PASSWD_FILE" \
  "${MQTT_USER:-sentinel-backend}" "${MQTT_PASS:-changeme}"

mosquitto_passwd -b "$PASSWD_FILE" \
  "${MQTT_DEVICE_USER:-sentinel-device}" "${MQTT_DEVICE_PASS:-changeme}"

chmod 600 "$PASSWD_FILE"
echo "[entrypoint] Password file written for: ${MQTT_USER:-sentinel-backend}, ${MQTT_DEVICE_USER:-sentinel-device}"

cp "$BASE_CONFIG" "$RUNTIME_CONFIG"

if [ -f "$CERTS_DIR/ca.crt" ] && [ -f "$CERTS_DIR/server.crt" ] && [ -f "$CERTS_DIR/server.key" ]; then
  echo "[entrypoint] Certs found — enabling TLS listener on :8883"
  cat >> "$RUNTIME_CONFIG" <<EOF

# TLS listener — auto-enabled by docker-entrypoint.sh (certs present in /mosquitto/certs)
listener 8883
protocol mqtt
cafile $CERTS_DIR/ca.crt
certfile $CERTS_DIR/server.crt
keyfile $CERTS_DIR/server.key
require_certificate false
tls_version tlsv1.3
EOF
else
  echo "[entrypoint] No certs in $CERTS_DIR — TLS listener skipped"
  echo "[entrypoint] Run: bash scripts/gen-mqtt-certs.sh && docker compose restart mosquitto"
fi

exec mosquitto -c "$RUNTIME_CONFIG"
