# Context

## ข้อบกพร่องสำคัญ

1. **Backend น่าจะ compile ไม่ผ่าน**
   DeviceEnrollmentService.java และบรรทัด 126 เรียก `auditService.log(...)` แค่ 4 argument แต่ AuditService.java รับ 5 argument  
   วิธีแก้: ส่ง `resource`, `detail`, `ipAddress` ให้ครบ เช่น `auditService.log(user, "ENROLLMENT_TOKEN_ISSUED", "/api/v1/devices/{id}/enrollment-token", "deviceId=...", null)`.

2. **RLS multi-tenancy เขียนไว้ใน SQL แต่ไม่มีโค้ดตั้งค่า `app.org_id` จริง**
   V7__row_level_security.sql ระบุว่าต้อง `SET LOCAL app.org_id = ...` แต่ค้นทั้ง `backend/src/main/java` พบแค่ `TenantContext.set(...)` ใน JWT filter ไม่มี Hibernate interceptor/connection hook  
   ผลคือ RLS อาจบล็อก query ทั้งหมด หรือไม่ทำงานตามที่ออกแบบ ขึ้นกับ DB role/connection  
   วิธีแก้: เพิ่ม transaction-aware interceptor เช่น `StatementInspector`/Hibernate event/`DataSource` wrapper ที่ตั้ง `SET LOCAL app.org_id` ทุก transaction หรือถ้ายังไม่พร้อม ให้ถอด `FORCE ROW LEVEL SECURITY` ออกก่อนใน migration production.

3. **Alert/Audit RLS ใช้ `organization_id` แต่ entity ไม่ map และ service ไม่ set**
   SQL เพิ่ม `alerts.organization_id` และ `audit_logs.organization_id` ที่ V7__row_level_security.sql  แต่ Alert.java และ AuditLog.java ไม่มี field นี้  
   วิธีแก้: เพิ่ม `organizationId` ใน entity แล้ว set จาก device/org context ตอนสร้าง alert/audit; เพิ่ม test ว่า tenant A ไม่เห็น alert/audit tenant B.

4. **WebSocket เปิดสาธารณะและอนุญาต origin ทุกที่**
   SecurityConfig.java `permitAll()` สำหรับ `/ws/**` และ WebSocketConfig.java ใช้ `setAllowedOriginPatterns("*")`  
   วิธีแก้: ตรวจ JWT ตอน handshake, จำกัด origin จาก `CORS_ALLOWED_ORIGINS`, และ filter broadcast ตาม tenant ไม่ใช่ส่ง telemetry ทุก device ให้ทุก session.

5. **Frontend เก็บ access/refresh token ใน `localStorage`**
   useAuth.js และ client.js ใช้ `localStorage` ทำให้ XSS ขโมย token ได้ง่าย  
   วิธีแก้: ใช้ HttpOnly Secure SameSite cookie สำหรับ refresh token, access token อายุสั้นใน memory, เพิ่ม CSP และ refresh flow ที่ไม่ expose token ให้ JS.

6. **Rate limit ถูก bypass ได้ด้วย header ปลอม**
   RateLimitFilter.java เชื่อ `X-Forwarded-For` โดยตรง และใช้ in-memory map ต่อ instance  
   วิธีแก้: อ่าน forwarded header เฉพาะเมื่อ request มาจาก trusted proxy, ใช้ Redis/Bucket4j distributed bucket, แยก limit สำหรับ login/refresh/enroll ให้เข้มกว่า API ทั่วไป.

7. **Telemetry v2 dynamic schema ยังชนกับ column บังคับ `temperature`/`humidity`**
   Telemetry.java บังคับ `temperature` และ `humidity` non-null แต่ v2 payload ใน TelemetryMessage.java อาจมี sensor อื่นโดยไม่มี field เหล่านี้  
   วิธีแก้: ทำ column fixed fields ให้ nullable หรือแยก `telemetry_readings`/JSONB เป็นแหล่งหลักจริง; ปรับ validation ไม่บังคับ temp/humidity เมื่อ `schemaVersion >= 2` และมี `readings`.

8. **MQTT validation ทำให้ v2 ใช้งานไม่ได้เต็มรูปแบบ**
   MqttConsumerService.java บังคับ `temperature` และ บังคับ `humidity` แม้เอกสารรองรับ `readings`  
   วิธีแก้: validate แยก v1/v2; v2 ต้อง validate `readings` map, sensor key, value type, quality, timestamp bounds.

9. **Kafka consumer ทำ side effect ก่อน persist สำเร็จ**
   KafkaTelemetryConsumer.java อัปเดต Redis สร้าง alert/notification broadcast ก่อน `saveAll` ที่ บรรทัด 138  
   วิธีแก้: persist ก่อน แล้วค่อย publish event หลัง commit เช่น transactional outbox; ใส่ idempotency key กัน alert/notification ซ้ำเมื่อ Kafka retry.

10. **Device name uniqueness สับสนระหว่าง per-org กับ global**
   DeviceService.java เช็คซ้ำเฉพาะใน org แต่ Device.java และ migration เดิมบังคับ `name UNIQUE` global  
   วิธีแก้: เลือกอย่างใดอย่างหนึ่งให้ชัด ถ้าต้อง per-org ให้เปลี่ยน DB เป็น unique `(organization_id, name)` และแก้ MQTT lookup; ถ้าต้อง global ให้ service เช็ค global แล้วคืน 400 แบบชัดเจน.

11. **Frontend Docker build ล้มแน่นอนจาก lockfile**
   frontend/Dockerfile  ใช้ `npm ci` แต่ไม่มี `package-lock.json`; ผมรัน `npm ci --dry-run` แล้วได้ error ว่า `npm ci` ต้องมี lockfile  
   วิธีแก้: generate และ commit `package-lock.json` หรือเปลี่ยนเป็น `npm install` แต่ production ควรใช้ lockfile.

12. **Frontend Dockerfile copy `public*` แต่ไม่มีโฟลเดอร์ public**
   frontend/Dockerfile copy `/app/public*` ขณะที่ project ไม่มี `frontend/public`  
   วิธีแก้: เพิ่มโฟลเดอร์ `public/.gitkeep` หรือแก้ Dockerfile ให้ `RUN mkdir -p public` / copy แบบไม่พังเมื่อไม่มี asset.

13. **MSK production ตั้ง client-broker เป็น PLAINTEXT**
   infra/terraform/modules/msk/main.tf ใช้ `client_broker = "PLAINTEXT"`  
   วิธีแก้: ใช้ `TLS` หรือ `TLS_PLAINTEXT` เฉพาะช่วง migration, เปิด authentication/IAM/SASL ตามมาตรฐานองค์กร, แล้วปรับ Spring Kafka bootstrap/security config.

14. **Refresh token เก็บ plaintext ใน DB**
   RefreshToken.java เก็บ token ตรงๆ  
   วิธีแก้: เก็บ hash เช่น SHA-256/HMAC ของ refresh token, lookup ด้วย hash, rotate token แบบ atomic และเพิ่ม device/session metadata เพื่อ revoke เฉพาะ session ได้.

15. **`@Async` ไม่ทำงาน**
   AuditService.java ใช้ `@Async` แต่ SentinelIotApplication.java มีแค่ `@EnableScheduling` ไม่มี `@EnableAsync`  
   วิธีแก้: เพิ่ม `@EnableAsync` และ configure bounded executor; หรือเอา `@Async` ออกถ้าตั้งใจให้ audit เป็น synchronous.
