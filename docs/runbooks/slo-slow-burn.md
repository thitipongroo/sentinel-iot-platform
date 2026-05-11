# Runbook: SentinelSLOSlowBurn

**Alert:** `SentinelSLOSlowBurn`
**Severity:** Warning (ticket)
**Trigger:** 24-hour error budget burn rate > 3× — budget will exhaust in ~10 days at this rate

---

## Impact

No immediate user-facing emergency. The platform is slightly less reliable than target,
and the monthly error budget is being consumed faster than sustainable. Must be investigated
before the weekly error budget review.

---

## Diagnosis

This alert fires during business hours and should be triaged in the next sprint cycle.

### 1. Correlate with recent changes
```bash
# Deploys in last 24h
kubectl rollout history deployment/sentinel-backend -n sentinel | head -5
git log --oneline --since="24 hours ago"
```

### 2. Identify low-volume but persistent errors
```bash
# Aggregate error counts by endpoint over 24h
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=increase(http_server_requests_seconds_count{job="sentinel-backend",status=~"5.."}[24h])' | \
  jq '.data.result | sort_by(.value[1] | tonumber) | reverse'
```

### 3. Check for misconfigured thresholds
Alert false positives can occur if:
- `TEMP_THRESHOLD`, `HUMIDITY_THRESHOLD`, or `SMOKE_THRESHOLD` are too tight (triggering mass alert notifications that cascade)
- The KEDA `lagThreshold` is too low (causing constant scale events that briefly interrupt processing)

---

## Remediation

1. Create a Jira/Linear ticket with the Prometheus link and burn rate value
2. Identify root cause (see Diagnosis above)
3. Deploy fix in the next sprint — no emergency deployment needed
4. Monitor burn rate after deploy to confirm recovery

---

## Notes

If this alert persists for more than 3 days without a root cause, escalate to the
platform engineering lead. Chronic slow burn often indicates a systemic issue
(misconfigured retry policy, wrong timeout value, or a hidden memory leak).
