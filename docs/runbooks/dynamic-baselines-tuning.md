# Runbook: Dynamic Baselines Tuning & Anomaly Detection Troubleshooting

**Service:** Sentinel IoT Platform — Realtime AI & Predictive Anomaly Engine (RFC-001 Phase 4)  
**Component:** `StreamingAnomalyInferenceService`, `AdaptiveThresholdService`, `MultivariateAnomalyDetector`  
**Primary Metrics:** `sentinel.anomaly.detected.count`, `sentinel.anomaly.inferences.count`, `sentinel.inference.duration.seconds`

---

## 1. Overview & Architecture

The Sentinel IoT Predictive Anomaly Engine analyzes live streaming telemetry on Kafka (`telemetry.raw`) to catch early equipment failures and thermal runaway events before physical damage occurs:

1. **Multivariate Isolation Forest:** Calculates multivariate isolation depth score ($s \in [0, 1]$) combining temperature, rate-of-rise ($\Delta T / \Delta t$), smoke PPM, and humidity.
2. **Adaptive Dynamic Threshold Bands:** Maintains rolling statistical baselines ($\mu \pm k\sigma$) per device per sensor using Exponential Moving Averages (EMA) with anomaly dampening.
3. **Physics Pattern Validator:** Confirms thermal runaway when $\Delta T > 2.0^\circ\text{C/min}$, smoke $> 80\text{ PPM}$, and temperature $> 45^\circ\text{C}$.

```
Kafka (telemetry.raw)
        │
        ▼
StreamingAnomalyInferenceService
   ├── 1. Multivariate Isolation Forest Scoring (score >= 0.65)
   ├── 2. Adaptive Threshold Evaluation (x > μ + 3σ)
   ├── 3. Physics Thermal Runaway Classifier
   └── 4. Distributed Alert Deduplication (Redis 5m cooldown)
```

---

## 2. Key Parameters & Configuration

Parameters are configured via application properties or environment variables:

| Parameter | Environment Variable | Default | Description |
|-----------|----------------------|---------|-------------|
| Alpha Learning Rate ($\alpha$) | `ANOMALY_ADAPTIVE_ALPHA` | `0.05` | Weight given to new observations in EMA ($\mu_t = (1-\alpha)\mu_{t-1} + \alpha x_t$). |
| Sigma Multiplier ($k$) | `ANOMALY_ADAPTIVE_SIGMA_MULTIPLIER` | `3.0` | Threshold band width ($\mu \pm k\sigma$). $k=3.0$ corresponds to $99.73\%$ confidence. |
| Minimum Sigma Floor ($\sigma_{min}$) | `ANOMALY_ADAPTIVE_MIN_SIGMA_FLOOR` | `0.2` | Minimum standard deviation floor to prevent ultra-narrow bands when sensors report constant values. |
| Minimum Baseline Samples | `ANOMALY_ADAPTIVE_MIN_SAMPLES` | `30` | Minimum readings required before dynamic thresholds activate. |
| Anomaly Dampening Factor | `ANOMALY_ADAPTIVE_DAMPENING` | `0.1` | Multiplier reducing $\alpha$ when an anomaly is present so outliers do not distort the baseline. |

---

## 3. Incident Scenarios & Remediation

### Scenario 1: High False Positive Rate (FPR Storm)

**Symptoms:**
- Spikes in `sentinel.anomaly.detected.count` without actual equipment failures.
- Operators reporting alert fatigue in factory halls experiencing seasonal temperature swings or process restarts.

**Diagnosis:**
1. Check Prometheus anomaly metric:
   ```promql
   sum(rate(sentinel_anomaly_detected_count_total[5m])) / sum(rate(sentinel_anomaly_inferences_count_total[5m])) * 100
   ```
   *Threshold:* Alert if FPR $> 0.5\%$.
2. Inspect current baselines for the affected device:
   ```bash
   curl -s -H "Authorization: Bearer $JWT" \
     http://localhost:8080/api/v1/analytics/anomalies/baselines/{deviceId} | jq .
   ```
3. Check if $\sigma$ has collapsed to near zero (e.g. constant sensor readings during shutdown followed by factory startup).

**Remediation:**
1. **Increase $\sigma$-multiplier ($k$)** from $3.0$ to $3.5$ or $4.0$ to widen tolerance bands:
   ```bash
   kubectl set env deployment/sentinel-iot-backend \
     ANOMALY_ADAPTIVE_SIGMA_MULTIPLIER="3.5" -n sentinel-prod
   ```
2. **Increase Minimum $\sigma$-floor** to prevent hyper-sensitive bands:
   ```bash
   kubectl set env deployment/sentinel-iot-backend \
     ANOMALY_ADAPTIVE_MIN_SIGMA_FLOOR="0.5" -n sentinel-prod
   ```
3. If an entire device cluster was just recalibrated or moved, reset their baselines (see Section 4).

---

### Scenario 2: Baseline Corruption / Drift

**Symptoms:**
- A sensor had a prolonged slow leak or gradual overheating over days; the adaptive baseline adjusted to the bad state and now reports anomalous values as "normal" ($\mu$ drifted upward).

**Diagnosis:**
1. Compare raw sensor readings against historical rollups:
   ```bash
   curl -s -H "Authorization: Bearer $JWT" \
     "http://localhost:8080/api/v1/analytics/rollups/daily?deviceId={deviceId}&from=2026-09-01T00:00:00Z&to=2026-10-13T00:00:00Z"
   ```
2. Check baseline $\mu$: If current $\mu = 65^\circ\text{C}$ while normal operating baseline was $24^\circ\text{C}$, the baseline has drifted.

**Remediation:**
1. **Reset baseline for the device or specific sensor:**
   ```bash
   curl -X POST -H "Authorization: Bearer $JWT" \
     http://localhost:8080/api/v1/analytics/anomalies/baselines/{deviceId}/reset
   ```
2. The engine requires 30 fresh nominal readings to rebuild an accurate baseline ($\mu, \sigma$).

---

### Scenario 3: High ML Inference Latency ($p95 > 20\text{ms}$)

**Symptoms:**
- `sentinel_inference_duration_seconds{quantile="0.95"} > 0.020`.
- Kafka consumer lag builds up on `telemetry.raw`.

**Diagnosis:**
1. Check CPU utilization across backend pods:
   ```bash
   kubectl top pods -l app.kubernetes.io/component=backend -n sentinel-prod
   ```
2. Verify Redis latency for baseline lookup:
   ```bash
   redis-cli --latency -h sentinel-redis
   ```
   *Threshold:* Redis latency should be $< 1.5\text{ms}$.

**Remediation:**
1. Scale out backend pods horizontally via KEDA:
   ```bash
   kubectl scale deployment sentinel-iot-backend --replicas=8 -n sentinel-prod
   ```
2. The Isolation Forest implementation in `MultivariateAnomalyDetector` is stateless and $O(\text{tree\_depth} \times \text{trees})$; horizontal scaling linearly reduces inference load per replica.

---

## 4. Emergency Operational Procedures

### 4.1. Reset Device Baseline via API
```bash
# Reset all sensors on device
curl -X POST \
  -H "Authorization: Bearer $ADMIN_JWT" \
  -H "Content-Type: application/json" \
  http://sentinel-backend:8080/api/v1/analytics/anomalies/baselines/{deviceId}/reset

# Reset single sensor (e.g. temperature)
curl -X DELETE \
  -H "Authorization: Bearer $ADMIN_JWT" \
  http://sentinel-backend:8080/api/v1/analytics/anomalies/baselines/{deviceId}/temperature
```

### 4.2. Direct Redis Baseline Clear (Break-Glass)
If backend API is unresponsive, flush baselines directly in Redis:
```bash
# Connect to Redis
redis-cli -h sentinel-redis -p 6379

# Find keys for tenant/device
KEYS sentinel:baseline:*:*

# Delete specific device baseline
DEL sentinel:baseline:{orgId}:{deviceId}:temperature
DEL sentinel:baseline:{orgId}:{deviceId}:humidity
DEL sentinel:baseline:{orgId}:{deviceId}:smokePpm
```

### 4.3. Test Inference Dry-Run (No Alert Created)
Test whether a hypothetical reading would trigger an anomaly:
```bash
curl -X POST \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  http://sentinel-backend:8080/api/v1/analytics/anomalies/evaluate \
  -d '{
    "deviceId": "00000000-0000-0000-0000-000000000001",
    "temperature": 88.5,
    "humidity": 12.0,
    "smokePpm": 240.0,
    "timestamp": "2026-10-13T12:00:00Z"
  }' | jq .
```
Expected output:
```json
{
  "isAnomaly": true,
  "anomalyScore": 0.94,
  "anomalyType": "THERMAL_RUNAWAY",
  "severity": "CRITICAL",
  "confidence": 0.98,
  "contributions": {
    "temperature": 0.45,
    "smokePpm": 0.38,
    "humidity": 0.17
  }
}
```
