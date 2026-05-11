# Context

1. Security Issues (Critical)

1.1 มี Hardcoded Default Credentials
หลักฐาน

พบใน:

backend/src/main/java/com/sentinel/iot/config/DataInitializer.java
frontend/src/app/login/page.jsx
เอกสาร README และ docs หลายจุด

Credential:

admin / admin123
operator / op123
ปัญหา

นี่เป็น security anti-pattern ระดับร้ายแรงถ้า deploy จริง:

attacker เดา password ได้ทันที
credential ถูก commit ลง git
bot scan internet สามารถ brute-force ได้ง่าย
credential reuse risk
ผลกระทบ
ระบบโดน takeover
dashboard/control panel ถูกเข้าถึง
telemetry ถูกแก้ไข
alert spam
privilege escalation
วิธีแก้
ระยะสั้น
ลบ default credential ออกจาก source code
force password setup ตอน first boot
disable bootstrap account หลัง initial setup
ระยะ production

ใช้:

Keycloak
Auth0
Cognito
OIDC/SAML

และ:

MFA
password policy
session revocation
anomaly login detection
1.2 README ยังใช้ Placeholder GitHub URL
หลักฐาน

README:

<https://github.com/yourusername/sentinel-iot-platform>
ปัญหา

สะท้อนว่า project ยังไม่ production-polished:

onboarding ไม่ complete
CI badge อาจ broken
credibility ต่ำเวลา recruiter/client ดู
วิธีแก้
replace ด้วย repo จริง
verify CI badge
เพิ่ม release/version tags
1.3 LINE Notify Deprecated แล้ว
หลักฐาน

README ระบุเอง:

LINE Notify (deprecated — replace with LINE Messaging API)
ปัญหา

LINE Notify ถูก deprecated แล้ว

ระบบ notification production จริงจะมี risk:

API ปิดในอนาคต
token invalid
notification fail ทั้งระบบ
วิธีแก้

เปลี่ยนเป็น:

LINE Messaging API
Telegram Bot API
Slack webhook
PagerDuty
Opsgenie

พร้อม abstraction layer:

NotificationProvider
 ├── LineProvider
 ├── SlackProvider
 ├── EmailProvider
 └── PagerDutyProvider
1.4 MQTT ยังมี Development Security Configuration
หลักฐาน

docs/architecture.md:

allow_anonymous true for development
ปัญหา

ถ้า config นี้หลุดขึ้น production:

ใครก็ publish telemetry ได้
spoof sensor ได้
flood MQTT broker ได้
inject fake alerts ได้
วิธีแก้

Production ต้องใช้:

mTLS
per-device cert
ACL isolation
topic authorization
broker-side rate limiting
device identity registry
2. Architecture Problems
2.1 System Complexity สูงเกินจำเป็นสำหรับ Scale ปัจจุบัน
สิ่งที่พบ

ระบบใช้:

Kafka
Redis
PostgreSQL partitioning
Argo Rollouts
KEDA
Terraform
Helm
ArgoCD
Replay Queue
Circuit Breaker
Jaeger
SLO burn-rate
ปัญหา

เกิด “Architecture Inflation”

คือ:

Complexity โตเร็วกว่า business value

ความเสี่ยง

ทีมเล็กจะเจอ:

maintenance burden
onboarding ยาก
infra debugging ซับซ้อน
operational fatigue
cost สูง
ตัวอย่าง

สำหรับ workload ระดับ:

1,000 req/s

จริง ๆ ยังไม่จำเป็นต้องมี:

Kafka + KEDA + MSK
multi-window SLO burn-rate
Argo Rollouts
replay architecture ซับซ้อน
วิธีแก้

แยกเป็น 3 maturity stage:

Stage Architecture
MVP MQTT + Spring + PostgreSQL
Growth เพิ่ม Redis + WebSocket
Scale เพิ่ม Kafka + KEDA + replay queue

จะ maintain ง่ายกว่า

2.2 Redis ถูกใช้หลายบทบาทเกินไป
จาก README

Redis ถูกใช้เป็น:

latest cache
replay queue
WS pub/sub
token blocklist
ปัญหา

Redis กลายเป็น:

Single Shared Operational Dependency

ถ้า Redis มีปัญหา:

dashboard ช้า
replay fail
websocket fail
auth revoke fail
ความเสี่ยง

เกิด cascading failure ได้ง่าย

วิธีแก้

แยก responsibility:

Function Recommended
Cache Redis
Queue Kafka/RabbitMQ
Token blacklist Redis separate namespace
WS fanout Dedicated pub/sub

หรืออย่างน้อย:

separate Redis DB
memory policy แยก
eviction isolation
2.3 Replay Queue Reliability ยังไม่ Strong Enough
จาก README

Replay queue:

Redis RPUSH
Drain every 30s
ปัญหา

Redis list ไม่ใช่ durable queue จริงระดับ production-critical

ถ้า:

Redis restart
memory eviction
corrupted appendonly

ข้อมูล telemetry อาจหาย

วิธีแก้

ใช้:

Kafka
RabbitMQ quorum queues
Pulsar

หรืออย่างน้อย:

Redis AOF everysec
dead-letter replay
replay idempotency
message checksum
2.4 ยังไม่เห็น Backpressure Strategy ชัดเจน
สิ่งที่ตรวจพบ

มี:

MQTT consumer
realtime websocket
replay queue

แต่ไม่พบ:

explicit backpressure design
bounded ingestion strategy
overload shedding
ความเสี่ยง

ถ้า telemetry burst:

memory spike
websocket lag
DB saturation
queue amplification
วิธีแก้

ต้องมี:

bounded queues
adaptive throttling
ingestion quotas
load shedding
circuit breaking per downstream
3. Database & Data Model Problems
3.1 Partitioning ยังไม่เห็น Retention Enforcement
พบ

README ระบุ:

Partitioned by month

แต่ยังไม่พบ:

retention cleanup automation
archival lifecycle
cold storage strategy
ปัญหา

IoT telemetry โตเร็วมาก

ถ้าไม่มี retention:

storage explode
index bloat
vacuum pressure
query degradation
วิธีแก้

กำหนด:

Data Type Retention
Raw telemetry 7-30 วัน
Hourly aggregate 1 ปี
Critical alerts 3-5 ปี

และใช้:

TimescaleDB
partition pruning
S3 archival
3.2 Hourly Aggregation ยังไม่ชัดเรื่อง Consistency
พบจาก README

มี:

hourly aggregates

แต่ยังไม่พบ:

aggregation job strategy
late-arrival handling
exactly-once semantics
ปัญหา

IoT data มักมา late/out-of-order

aggregation อาจผิด

วิธีแก้

ใช้:

event-time windows
watermark strategy
immutable raw events
recomputation jobs
4. Frontend Problems
4.1 Frontend ยังผูกกับ Backend Schema ค่อนข้างแน่น
พบ

มี generated API types:

frontend/src/api/generated/types.ts
ปัญหา

ถ้า backend schema เปลี่ยน:

frontend break ง่าย
deploy coordination ยาก
วิธีแก้

เพิ่ม:

BFF layer
contract versioning
feature flags
backward-compatible DTO policy
4.2 ยังไม่เห็น Offline Conflict Strategy
แม้มี OfflineBanner

แต่ยังไม่พบ:

offline write sync
conflict resolution
stale telemetry reconciliation
ปัญหา

dashboard realtime production จริง:

network flapping เกิดบ่อย
websocket disconnect บ่อย
วิธีแก้

เพิ่ม:

reconnect jitter
snapshot resync
optimistic cache invalidation
sequence ordering
5. DevOps / Infrastructure Issues
5.1 Docker Compose Stack ใหญ่มาก
พบ

Compose มี:

postgres
redis
kafka
grafana
prometheus
jaeger
mosquitto
frontend
backend
ปัญหา

local onboarding หนักมาก

developer ใหม่จะเจอ:

RAM exhaustion
slow startup
flaky environment
compose instability
วิธีแก้

แยก profile:

compose.dev.yml
compose.observability.yml
compose.full.yml
5.2 Terraform + Helm + ArgoCD Complexity สูง
ปัญหา

ตอนนี้ infra stack มี:

Terraform
Helm
ArgoCD
Rollouts
KEDA
ความเสี่ยง

Configuration drift สูงมาก

วิธีแก้

กำหนด ownership ชัด:

Tool Responsibility
Terraform Infra only
Helm App templating
ArgoCD GitOps deployment

และ:

lock version
environment promotion policy
infra validation pipeline
6. Testing Problems
6.1 จำนวน Test ยังน้อยเมื่อเทียบกับ Complexity
ตรวจพบ

Backend main files:

69 files

Backend tests:

12 files
ปัญหา

coverage ดูไม่สมดุลกับ complexity ของระบบ

โดยเฉพาะ:

replay queue
websocket
MQTT failure
concurrency
retry/circuit breaker
วิธีแก้

เพิ่ม:

chaos tests
soak tests
concurrency tests
websocket fanout tests
MQTT storm tests
replay consistency tests
6.2 ยังไม่เห็น Security Testing Framework
ยังไม่พบ
SAST
dependency scanning
secret scanning
DAST
ความเสี่ยง

supply chain vulnerability

วิธีแก้

เพิ่ม:

Trivy
Snyk
Dependabot
Gitleaks
OWASP ZAP
7. Observability Problems
7.1 Metrics เยอะ แต่ Business Metrics ยังไม่ชัด
พบ

มี:

Prometheus
Jaeger
SLO burn-rate

แต่ยังไม่เห็น:

tenant usage metrics
device health scoring
ingestion success ratio per customer
ปัญหา

ระบบ monitor ได้เชิง technical แต่ยัง monitor business impact ไม่ดี

วิธีแก้

เพิ่ม:

active devices
sensor failure rate
alert fatigue metrics
customer SLA metrics
8. Product & Real-World Gaps
8.1 ยังไม่เห็น Device Provisioning Lifecycle จริง

แม้มี lifecycle:

PROVISIONED → ACTIVE → INACTIVE → DECOMMISSIONED

แต่ยังไม่พบ:

secure onboarding
device enrollment
certificate rotation
remote revoke
ปัญหา

IoT production จริงจุดนี้สำคัญมาก

วิธีแก้

เพิ่ม:

device PKI
enrollment tokens
secure bootstrap
OTA certificate rotation
8.2 ยังไม่เห็น Multi-Tenant Isolation ที่ Strong จริง

แม้มี:

organizationId

แต่ยังไม่เห็น:

row-level security
tenant resource isolation
quota isolation
tenant-specific encryption
ความเสี่ยง

tenant data leakage

วิธีแก้

เพิ่ม:

PostgreSQL RLS
tenant-aware cache keys
tenant rate limits
audit trails


