# Security — Sentinel IoT Platform

---

## Features

| Feature | Implementation |
| --- | --- |
| Authentication | JWT (15 min access token, stored in JS module-level variable — never localStorage) + opaque refresh token (7 days, DB-persisted as **SHA-256 hash**, rotated on every use, delivered as `HttpOnly; Secure; SameSite=Strict` cookie — never in response body) |
| Access Token Revocation | `POST /auth/logout` adds the token's JTI to a Redis blocklist on DB 1 (TTL = remaining token lifetime). Every request checks the blocklist — stolen or logged-out tokens are rejected immediately. |
| Zero-Downtime Key Rotation | Set `JWT_PREVIOUS_SECRET=<old>` + `JWT_SECRET=<new>` and redeploy. Tokens signed with the old key remain valid until they expire (max 15 min); old `JWT_PREVIOUS_SECRET` can be cleared after that. |
| Refresh Token Reuse Detection | `rotateRefreshToken()` calls `revokeAllByUsername()` when a revoked token is presented — token family invalidation per RFC 6819. |
| Device Enrollment | One-time 256-bit SecureRandom tokens; only SHA-256 hash stored in DB; single-use; TTL-bound (default 24 h); bound to a specific device ID. Devices bootstrap via `POST /devices/enroll` (unauthenticated — token is the credential) and receive per-device MQTT credentials. |
| Rate Limiting | Bucket4j — tiered limits per IP: **10 req/min** for auth endpoints (`/api/v1/auth/*`), **100 req/min** for all other API endpoints. X-Forwarded-For trusted only from configured proxy IPs (`rate-limit.trusted-proxies`). In-process buckets — see Known Limitations. |
| RBAC | `ADMIN` + `OPERATOR` roles; method-level `@PreAuthorize` |
| CORS | Restricted to `CORS_ALLOWED_ORIGINS` env var (default: `http://localhost:3000`). Headers limited to `Authorization`, `Content-Type`, `X-Request-ID`. |
| CSRF | Disabled — correct for a stateless JWT API. |
| Secret Management | `JWT_SECRET` required at runtime — no default, no fallback. **Production upgrade path:** inject via HashiCorp Vault (`spring-cloud-vault`) or AWS Secrets Manager. |
| Audit Logging | Every auth event, alert acknowledgement, device mutation, and enrollment event persisted to `audit_logs` with username + IP. |
| Audit Retention | `audit_logs` purged daily at 03:30 (`AUDIT_RETENTION_DAYS`, default 90 days). |
| MQTT Auth | `allow_anonymous false` enforced. Per-device accounts via `MQTT_DEVICE_CREDENTIALS=sensor-1:pass1,sensor-2:pass2`. |
| MQTT TLS / mTLS | TLS on `:8883` when certs present. `MQTT_TLS_REQUIRED=true` removes plaintext `:1883`. `MQTT_MTLS_ENABLED=true` requires client certificates. See [mqtt-tls.md](mqtt-tls.md). |
| Multi-tenant Isolation | `organizationId` scoping + PostgreSQL Row Level Security (`V7__row_level_security.sql`) enforced by `TenantRlsAspect` (Spring AOP `@Before` on every `@Transactional` method — issues `SET LOCAL app.org_id` inside the transaction) + tenant-namespaced Redis keys (`device:{orgId}:{deviceId}`) |
| Secret Scanning | Gitleaks runs on every CI push — secrets committed to git fail the build |
| Dependency/Container Scan | Trivy scans filesystem (SCA) and backend container image on every CI push — CRITICAL/HIGH CVEs fail the build. Results in GitHub Security tab (SARIF) |
| Actuator Exposure | `/actuator/health` public; internal details shown only to authenticated users. |
| Request Correlation | `X-Request-ID` echoed; `requestId`, `method`, `path`, `username`, `durationMs` in MDC per log line |

---

## Known Limitations

| Gap | Current State | Production Fix |
| --- | --- | --- |
| Rate limiting is in-process | Each replica has independent bucket — effective limit is `10/100 × N` replicas | Swap `ConcurrentHashMap` for `bucket4j-redis` (`ProxyManager` backed by Redis atomic counters) |
| No refresh-token device binding | Refresh tokens bound to user only, not to issuing device or IP | Add fingerprint (IP + User-Agent hash) on issuance; reject reuse from different fingerprint |
| TLS MQTT is opt-in | Plaintext `:1883` active by default in dev | Set `MQTT_TLS_REQUIRED=true` after running `gen-mqtt-certs.sh`; enforce in production via Helm `values-prod.yaml` |
| mTLS is opt-in | `require_certificate false` unless `MQTT_MTLS_ENABLED=true` | Run `gen-mqtt-certs.sh --with-client-certs`, mount client certs into each service, set `MQTT_MTLS_ENABLED=true` |
