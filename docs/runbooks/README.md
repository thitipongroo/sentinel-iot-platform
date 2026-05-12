# runbooks/ — Sentinel IoT Platform

Runbook ปฏิบัติการสำหรับใช้ระหว่าง incident หรือ on-call เพื่อ diagnose และ remediate ปัญหา

---

## Incident Response

| ไฟล์ | คำอธิบาย |
|------|----------|
| [incident-flow.md](incident-flow.md) | กระบวนการ incident response ตั้งแต่ detect จนถึง post-mortem — severity levels, escalation path, post-mortem template |

---

## SLO Burn Rate Alerts

| ไฟล์ | Severity | คำอธิบาย |
|------|----------|----------|
| [slo-fast-burn.md](slo-fast-burn.md) | 🔴 Critical | Error budget fast burn — immediate response required |
| [slo-medium-burn.md](slo-medium-burn.md) | 🟠 Warning | Error budget medium burn |
| [slo-slow-burn.md](slo-slow-burn.md) | 🟡 Info | Error budget slow burn — trend monitoring |
| [slo-budget-low.md](slo-budget-low.md) | 🟡 Info | Error budget ใกล้หมด — freeze non-critical changes |

---

## Performance Runbooks

| ไฟล์ | คำอธิบาย |
|------|----------|
| [latency-p95.md](latency-p95.md) | P95 latency เกิน SLO — ขั้นตอน investigation และ remediation |
| [latency-p99.md](latency-p99.md) | P99 latency เกิน SLO |
| [kafka-lag.md](kafka-lag.md) | Kafka consumer lag — วิธี diagnose และ recover |
| [kafka-lag-critical.md](kafka-lag-critical.md) | Kafka lag ระดับ critical — escalation path |
| [jvm-heap.md](jvm-heap.md) | JVM heap pressure — heap dump, GC tuning |

---

## Reliability Testing

| ไฟล์ | คำอธิบาย |
|------|----------|
| [chaos-testing.md](chaos-testing.md) | Chaos engineering — 5 failure injection scenarios (DB down, Redis down, pod kill, network partition, MQTT restart) |
| [failure-testing.md](failure-testing.md) | Failure mode testing checklist — 6 scenarios พร้อม trigger commands, verification steps และ per-release sign-off table |
