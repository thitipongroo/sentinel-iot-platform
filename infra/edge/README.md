# Sentinel Edge Ingestion Gateway & Store-and-Forward Topology

This directory contains the production configurations for Sentinel IoT Edge Gateways deployed in factory environments with intermittent WAN / 4G / satellite connectivity.

---

## 1. Store-and-Forward Architecture

```text
Factory LAN (Sub-millisecond)              Intermittent WAN Link                Sentinel Cloud Cluster
─────────────────────────────────────      ─────────────────────────────        ────────────────────────
[ PLC / Modbus Device ]  ─┐
                          ├─► [ Mosquitto Edge Broker ] ═══ (QoS 1 Bridge) ═══► [ EMQX / Cloud Gateway ]
[ OPC-UA Gateway ]       ─┤     (Disk queue: 256MB)                                       │
                          │                                                               ▼
[ Local Sensor Nodes ]   ─┘                                                         [ Kafka Pipeline ]
```

### Key Operational Characteristics:
1. **Local LAN Isolation:** Field devices publish telemetry to `127.0.0.1:1883` or factory gateway IP with sub-millisecond roundtrips and zero dependency on WAN availability.
2. **Durable Store-and-Forward:** If the factory's cellular/WAN uplink drops, the edge broker buffers up to 500,000 messages (256 MB) directly to persistent disk (`/mosquitto/data/mosquitto.db`).
3. **Automatic WAN Recovery:** Once WAN connectivity is restored, the edge broker seamlessly replays all queued QoS 1 messages to the cloud cluster without dropping a single reading.
4. **Device Twin / Shadow Bidirectional Routing:**
   - Egress: Device state reports (`.../shadow/update`) and queries (`.../shadow/get`) forwarded upstream.
   - Ingress: Cloud delta push events (`.../shadow/update/delta`) and ACKs delivered downstream to local devices.

---

## 2. Deployment Instructions

### Prerequisites
- Docker Engine & Docker Compose on edge IPC (Linux x86_64 or ARM64 / Raspberry Pi / Siemens IPC).
- TLS CA certificate placed at `infra/edge/certs/ca.crt`.

### Quick Start
```bash
# 1. Create environment file
cat <<EOF > .env
GATEWAY_ID=factory-bkk-01
GATEWAY_MQTT_USERNAME=edge-gateway-factory-bkk-01
GATEWAY_MQTT_PASSWORD=your_edge_token_here
EOF

# 2. Start the edge broker
docker-compose -f docker-compose.edge.yml up -d

# 3. Check bridge connection status
docker logs -f sentinel-edge-broker
```
