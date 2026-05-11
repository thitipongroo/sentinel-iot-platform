# Runbook: SentinelErrorBudgetLow

**Alert:** `SentinelErrorBudgetLow`
**Severity:** Warning (informational)
**Trigger:** Less than 10% of the 30-day error budget remains

---

## Impact

The platform has consumed over 90% of its monthly error budget. No new non-critical
deployments should be made until the budget recovers. The remaining ~4 minutes of
downtime must be protected.

---

## Immediate Actions

### 1. Freeze non-critical deployments
Announce in the engineering Slack channel:
> `[DEPLOY FREEZE] Error budget below 10%. Only P0 security fixes or rollbacks allowed until the budget resets on [date].`

### 2. Quantify remaining budget
```bash
curl -sG http://prometheus:9090/api/v1/query \
  --data-urlencode 'query=sentinel:error_budget_remaining:30d'
# 0.05 = 5% remaining = ~2 minutes of allowable downtime left this month
```

### 3. Review what consumed the budget
```bash
# Error rate trend over 30 days
curl -sG http://prometheus:9090/api/v1/query_range \
  --data-urlencode 'query=sentinel:http_error_ratio:rate5m' \
  --data-urlencode 'start=-30d' --data-urlencode 'step=1h'
```
Look for: incident spikes, bad deploys, or chronic low-level errors.

---

## Recovery

The error budget resets on the **first day of each month** (rolling 30-day window).
Recovery does NOT require any action — simply stop consuming the budget and let time pass.

If errors are still ongoing, follow [SentinelSLOFastBurn](./slo-fast-burn.md) or
[SentinelSLOMediumBurn](./slo-medium-burn.md) to stop the bleeding first.

---

## Post-period Review

At the next monthly reliability review, answer:
1. What were the top 3 error contributors?
2. Were they from the same root cause or different incidents?
3. What is the remediation to prevent recurrence?
4. Should the SLO target be adjusted (up = stricter, down = more realistic)?
