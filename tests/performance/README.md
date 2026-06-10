# tests/performance/ — k6 Performance Tests

k6 test suite covering all 38 test cases from the performance and load test plans.

## Prerequisites

```bash
# Standard k6 (all scenarios except kafka-load.js)
brew install k6   # macOS
# or: https://k6.io/docs/get-started/installation/

# xk6-mqtt build required only for kafka-load.js
go install go.k6.io/xk6/cmd/xk6@latest
xk6 build --with github.com/pmatseykanets/xk6-mqtt@latest
# produces ./k6 binary — use it in place of k6 for kafka-load.js
```

## Seed data

All scenarios require demo data. Run once before any test:

```bash
bash scripts/seed-demo.sh
```

## Scenarios

| File | Plan | Test cases | VU range | Duration |
|------|------|-----------|----------|----------|
| [normal-load.js](scenarios/normal-load.js) | performance-test-plan | 1.1.1–1.1.6, 1.3.1–1.3.2 | 50 | ~10 min |
| [kafka-load.js](scenarios/kafka-load.js) | performance-test-plan | 1.2.1–1.2.3, 2.4.1–2.4.5 | 500–2000 msg/s | ~5 min |
| [websocket.js](scenarios/websocket.js) | performance-test-plan | 1.4.1–1.4.3 | 100 / 500 | 5 min |
| [ramp-up.js](scenarios/ramp-up.js) | load-test-plan | 2.1.1–2.1.4 | 0→500 | ~17 min |
| [spike.js](scenarios/spike.js) | load-test-plan | 2.2.1–2.2.4 | 10→300 | ~11 min |
| [soak.js](scenarios/soak.js) | load-test-plan | 2.3.1–2.3.6 | 100 | ~130 min |
| [multi-tenant.js](scenarios/multi-tenant.js) | load-test-plan | 2.5.1–2.5.3 | 250 (5×50) | 10 min |

## Recommended run order

Run shorter/cheaper scenarios first; reserve soak for pre-release.

```bash
# 1. Ramp-up — find the breaking point first
k6 run tests/performance/scenarios/ramp-up.js \
  --out prometheus=http://localhost:9090/api/v1/write

# 2. Spike — verify recovery behaviour
k6 run tests/performance/scenarios/spike.js \
  --out prometheus=http://localhost:9090/api/v1/write

# 3. Normal load — SLO verification per endpoint
k6 run tests/performance/scenarios/normal-load.js \
  --out prometheus=http://localhost:9090/api/v1/write

# 4. WebSocket — 100 clients (1.4.1)
k6 run tests/performance/scenarios/websocket.js \
  -e WS_CLIENTS=100 -e DURATION=5m \
  --out prometheus=http://localhost:9090/api/v1/write

# 4b. WebSocket — 500 clients (1.4.2)
k6 run tests/performance/scenarios/websocket.js \
  -e WS_CLIENTS=500 -e DURATION=5m \
  --out prometheus=http://localhost:9090/api/v1/write

# 5. Kafka pipeline (requires xk6 binary)
./k6 run tests/performance/scenarios/kafka-load.js \
  -e RATE=500 \
  --out prometheus=http://localhost:9090/api/v1/write

# 6. Multi-tenant isolation under load
k6 run tests/performance/scenarios/multi-tenant.js \
  --out prometheus=http://localhost:9090/api/v1/write

# 7. Soak — only before major release (2 hours)
k6 run tests/performance/scenarios/soak.js \
  --out prometheus=http://localhost:9090/api/v1/write
# Quick smoke (30 min):
k6 run tests/performance/scenarios/soak.js -e SOAK_DURATION=20m
```

## Environment variables

| Variable | Default | Used by |
|----------|---------|---------|
| `BASE_URL` | `http://localhost:8080` | all |
| `USERNAME` | `admin` | all (single-org) |
| `PASSWORD` | `admin123` | all (single-org) |
| `WS_URL` | `ws://localhost:8080/ws/telemetry` | websocket.js |
| `WS_CLIENTS` | `100` | websocket.js |
| `DURATION` | scenario-specific | websocket.js, soak.js, multi-tenant.js |
| `RATE` | `500` | kafka-load.js (msgs/s) |
| `DLQ_TEST` | `false` | kafka-load.js (set `true` to inject malformed msgs) |
| `SOAK_DURATION` | `120m` | soak.js |
| `ORG_A_USER` … `ORG_E_USER` | `org-{name}-admin` | multi-tenant.js |
| `ORG_A_PASS` … `ORG_E_PASS` | `sentinel123` | multi-tenant.js |

## Results

JSON summaries are written to `tests/performance/results/` (git-ignored). Import into Grafana or inspect with:

```bash
cat tests/performance/results/ramp-up.json | jq '.metrics["http_req_duration{endpoint:devices-list}"].values'
```

## SLO reference

| Endpoint | P95 SLO | P99 SLO |
|----------|---------|---------|
| `GET /devices` | 200 ms | 400 ms |
| `GET /telemetry/{id}/latest` | 300 ms | 500 ms |
| `GET /telemetry/{id}/cache` | 50 ms | 100 ms |
| `GET /alerts` | 300 ms | 500 ms |
| `POST /auth/login` | 300 ms | — |
| WebSocket broadcast (100 clients) | 150 ms | 300 ms |
| WebSocket broadcast (500 clients) | 300 ms | 500 ms |
| Multi-tenant `/devices` (RLS overhead) | 230 ms | — |
