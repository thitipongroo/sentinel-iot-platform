# Backend Security Test Report

**วันที่รัน:** 2026-05-12  
**สถานะ:** ✅ ผ่านทั้งหมด  
**ผลรวม:** 45 tests | 8 files | 0 failures  
**Framework:** JUnit 5 + Spring Boot Test + Testcontainers  
**แผน:** [security-test-plan.md](../test-plans/security-test-plan.md)

---

## สรุปผล

| หมวด | Test Class | Tests | ผล |
|------|-----------|-------|-----|
| 1 — JWT & Authentication | `JwtSecurityTest` | 9 | ✅ |
| 2 — Refresh Token & Session | `RefreshTokenSecurityTest` | 7 | ✅ |
| 3 — Authorization / RBAC | `RbacSecurityTest` | 8 | ✅ |
| 4 — Multi-Tenant Isolation | `MultiTenantSecurityTest` | 6 | ✅ |
| 5 — Rate Limiting | `RateLimitSecurityTest` | 4 | ✅ |
| 6 — Input Validation | `InputValidationSecurityTest` | 5 | ✅ |
| 8 — WebSocket Security | `WebSocketSecurityTest` | 3 | ✅ |
| 9 — Error Handling | `ErrorHandlingSecurityTest` | 3 | ✅ |
| **รวม** | **8 files** | **45** | **✅** |

---

## JwtSecurityTest — 9 tests ✅

ทดสอบ JWT forgery, algorithm confusion, expiry, revocation, cross-org access และ key rotation

| Test | Attack Vector | ผลที่คาดหวัง |
|------|-------------|-------------|
| `algNoneToken_isRejected` | `alg:none` bypass | 403 |
| `tamperedSignature_isRejected` | Tampered JWT signature | 403 |
| `expiredToken_isRejected` | Expired token (past `exp` claim) | 403 |
| `revokedToken_afterLogout_isRejected` | JTI blocklist bypass | 403 |
| `tokenWithForeignOrgId_cannotAccessOtherOrgsDevice` | Cross-org IDOR via orgId | 404 |
| `tokenForNonExistentUser_isRejected` | Token for ghost user | 403 |
| `tokenSignedWithWrongSecret_isRejected` | Token signed with wrong key | 403 |
| `tokenSignedWithPreviousKey_isAcceptedDuringRotation` | Key rotation grace period | 200 |
| `basicAuthScheme_isRejected` | Non-Bearer auth scheme | 403 |

---

## RefreshTokenSecurityTest — 7 tests ✅

ทดสอบ token theft replay, single-use enforcement, RFC 6819 family revocation และ cookie security

| Test | Attack Vector | ผลที่คาดหวัง |
|------|-------------|-------------|
| `randomStringRefreshToken_isRejected` | Forged refresh token | 400 |
| `alreadyUsedRefreshToken_isRejected` | Rotated token replay | 400 |
| `tokenReuseDetection_revokesAllSessionsForUser` | Refresh token theft → family revocation | 400 (all sessions) |
| `refreshTokenNotExposedInResponseBody` | Token not in response body | body ไม่มี `refreshToken` field |
| `refreshCookieHasSecurityAttributes` | Cookie security flags | `HttpOnly; Secure; SameSite=Strict` |
| `logout_revokesAllRefreshTokensForUser` | Logout revokes all device sessions | 400 (both devices) |
| `expiredRefreshToken_isRejected` | Expired refresh token | 400 |

---

## RbacSecurityTest — 8 tests ✅

ทดสอบ privilege escalation, unauthenticated access และ IDOR cross-org

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `operator_cannotCreateDevice` | OPERATOR → POST /devices | 403 |
| `operator_cannotPatchLifecycle` | OPERATOR → PATCH lifecycle | 403 |
| `operator_cannotPatchFirmware` | OPERATOR → PATCH firmware | 403 |
| `operator_cannotAcknowledgeAlert` | OPERATOR → PUT acknowledge (@PreAuthorize) | 403 |
| `operator_cannotGenerateEnrollmentToken` | OPERATOR → POST enrollment-token (@PreAuthorize) | 403 |
| `operator_canReadDeviceList` | OPERATOR → GET /devices | 200 ✅ |
| `noToken_isRejected` | No Authorization header | 403 |
| `idor_foreignOrgJwt_cannotAccessOtherOrgsDevice` | Foreign org JWT → GET device | 404 |

---

## MultiTenantSecurityTest — 6 tests ✅

ทดสอบ cross-tenant data leakage, TenantContext isolation, RLS policy existence และ tampered orgId claim

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `foreignOrgToken_cannotSeeDevicesOfOtherOrg` | App-layer org filter (DeviceService) | devices = [] |
| `foreignOrgToken_cannotSeeAlertsOfOtherOrg` | RLS via TenantRlsAspect + Spring Data @Transactional | alerts = [] |
| `foreignOrgToken_cannotAccessTelemetryOfOtherOrgDevice` | Ownership check ใน TelemetryController | 404 |
| `rlsPolicies_existForTenantTables` | ตรวจ pg_policies ว่ามี RLS policies ครบ | policies exist |
| `tenantContext_isIsolatedPerRequest` | TenantContext ไม่รั่วข้าม requests | ข้อมูล org อื่น = [] |
| `tamperedOrgIdInPayload_isRejected` | Modified JWT payload, unchanged signature | 403 |

> **หมายเหตุ:** `foreignOrgToken_cannotSeeAlertsOfOtherOrg` พึ่งพา PostgreSQL RLS ทำงานกับ non-superuser DB user ตาม V7 migration comment

---

## RateLimitSecurityTest — 4 tests ✅

ทดสอบ brute-force protection และ IP spoofing prevention  
(Standalone filter test — ไม่ใช้ Spring context)

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `authEndpoint_isLimitedAt10RequestsPerMinute` | Request ที่ 11 บน auth endpoint | 429 |
| `apiEndpoint_isLimitedAt100RequestsPerMinute` | Request ที่ 101 บน API endpoint | 429 |
| `xForwardedFor_withoutTrustedProxy_doesNotBypassRateLimit` | Spoofed XFF header ไม่ข้าม rate limit | 429 (real IP ถูกนับ) |
| `differentIps_haveIndependentBuckets` | IP A exhausted → IP B ยังผ่านได้ | IP B = 200 |

---

## InputValidationSecurityTest — 5 tests ✅

ทดสอบ Bean Validation enforcement และ JPA parameterized query ป้องกัน SQL injection

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `nonSemverFirmwareVersion_isRejected` | `firmwareVersion: "not-a-version"` | 400 |
| `emptyDeviceName_isRejected` | `name: ""` (@NotBlank) | 400 |
| `sqlInjectionDeviceName_isStoredAsLiteralString` | `'; DROP TABLE devices; --` ถูก store verbatim | 201, name = literal string |
| `xssPayloadDeviceName_isStoredAsLiteralString` | `<script>alert(1)</script>` ถูก store verbatim | 201, name = literal string |
| `invalidLifecycleEnum_isRejected` | `lifecycleStatus: "INVALID_STATUS"` | 400 |

---

## WebSocketSecurityTest — 3 tests ✅

ทดสอบ `JwtWebSocketHandshakeInterceptor` โดยตรง (ไม่ใช้ MockMvc)

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `handshake_withNoToken_isRejected` | ไม่มี `?token=` query param | `beforeHandshake()` คืน `false` |
| `handshake_withInvalidToken_isRejected` | `?token=invalid-value` | `beforeHandshake()` คืน `false` |
| `handshake_withValidToken_storesOrgIdInAttributes` | Valid JWT → `beforeHandshake()` คืน `true`, `attributes["orgId"]` = UUID | ผ่าน |

---

## ErrorHandlingSecurityTest — 3 tests ✅

ทดสอบ user enumeration prevention, stack trace suppression และ 404 information disclosure

| Test | คำอธิบาย | ผลที่คาดหวัง |
|------|----------|-------------|
| `loginFailure_sameStatusForUnknownUserAndWrongPassword` | DaoAuthenticationProvider hides UsernameNotFoundException → response เหมือนกัน | HTTP status เท่ากัน |
| `validationError_doesNotExposeStackTrace` | GlobalExceptionHandler คืน ProblemDetail ไม่มี stack trace | body ไม่มี `at com.` |
| `nonexistentEndpoint_returns404WithoutInternalPaths` | GET /api/v1/nonexistent-endpoint-xyz | 404, body ไม่มี internal paths |
