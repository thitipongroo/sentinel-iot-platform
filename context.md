1. Frontend ยังดูเป็น dashboard layer มากกว่า product-grade UX

จาก structure:

dashboard
chart
device list
alert list

ยังไม่เห็น:

advanced filtering
virtualized tables
offline handling
optimistic update
accessibility strategy
design system
error boundary strategy
state normalization

ช่วยแก้ไขในสิ่งที่ยังไม่เห็นด้วย

2. ยังไม่เห็น API versioning strategy

ยังไม่พบ:

/v1
backward compatibility policy
schema evolution plan

ถ้าระบบโตจะเริ่ม migrate ยาก

ช่วยแก้ไขด้วย

3. ยังไม่พบ message schema governance

TelemetryMessage มีอยู่

แต่ยังไม่เห็น:

Avro
Protobuf
schema registry
compatibility validation

ระยะยาว event evolution จะลำบาก

ช่วยแก้ไขด้วย

4. ยังไม่พบ OpenAPI contract automation

แม้มี OpenApiConfig

แต่ยังไม่เห็น:

generated SDK
contract testing
schema validation pipeline

ช่วยแก้ไขด้วย


5. ยังไม่เห็น SLO/SLA strategy

มี observability แล้ว

แต่ยังไม่เห็น:

uptime target
latency budget
error budget
SLO alerting

ช่วยแก้ไขด้วย