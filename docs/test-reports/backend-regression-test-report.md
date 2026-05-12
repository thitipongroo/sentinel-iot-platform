# Backend Regression Test Report — Sentinel IoT Platform

**วันที่รัน:** 2026-05-12  
**สถานะ:** ✅ ผ่านทั้งหมด  
**จำนวน Tests:** 55 | 0 failures | 0 errors | 0 skipped  
**Package:** `com.sentinel.iot.regression`  
**Framework:** JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL 16 · Redis 7 · Mosquitto 2)

---

## ภาพรวม

| Test Class | หัวข้อ | Tests | ผล |
|------------|--------|-------|-----|
| `ApiContractRegressionTest` | 3.1 API Contract + 3.2 HTTP Status | 18 | ✅ |
| `AuthRegressionTest` | 3.3 Authentication & Token | 6 | ✅ |
| `RbacRegressionTest` | 3.4 RBAC Rules | 10 | ✅ |
| `MultiTenantRegressionTest` | 3.5 Multi-Tenant Isolation | 6 | ✅ |
| `MigrationRegressionTest` | 3.6 Database Migration | 5 | ✅ |
| `RateLimitRegressionTest` | 3.7 Rate Limiting Configuration | 5 | ✅ |
| `WebSocketRegressionTest` | 3.8 WebSocket Behavior | 5 | ✅ |
| **รวม** | | **55** | **✅** |

---

## 3.1 + 3.2 — ApiContractRegressionTest (18 tests)

| # | Test Method | ผล |
|---|-------------|-----|
| 3.1.1 | `loginResponseSchema_hasRequiredFieldsAndNoRefreshTokenInBody` | ✅ |
| 3.1.2 | `deviceListSchema_hasRequiredFields` | ✅ |
| 3.1.3 | `deviceDetailSchema_hasAllRequiredFields` | ✅ |
| 3.1.4 | `alertListSchema_hasRequiredFields` | ✅ |
| 3.1.5 | `telemetryStatsSchema_hasOnlyExpectedFields` | ✅ |
| 3.1.6 | `errorResponseSchema_hasProblemDetailFields_neverStackTrace` | ✅ |
| 3.1.7 | `errorResponseSchema_400_hasProblemDetailFormat` | ✅ |
| 3.1.8 | `deviceListEndpoint_isAnArray` | ✅ |
| 3.2.1 | `validLogin_returns200` | ✅ |
| 3.2.2 | `invalidLogin_returns401` | ✅ |
| 3.2.3 | `createDevice_asAdmin_returns201` | ✅ |
| 3.2.4 | `createDevice_asOperator_returns403` | ✅ |
| 3.2.5 | `getDevice_withRandomUuid_returns404` | ✅ |
| 3.2.6 | `createDevice_withBlankName_returns400` | ✅ |
| 3.2.7 | `patchLifecycle_onDecommissionedDevice_returns400` | ✅ |
| 3.2.8 | `malformedJsonBody_returns400` | ✅ |
| 3.2.9 | `refreshWithInvalidCookie_returns400` | ✅ |
| 3.2.10 | `unauthenticatedRequest_returns403` | ✅ |

---

## 3.3 — AuthRegressionTest (6 tests)

| # | Test Method | ผล |
|---|-------------|-----|
| 3.3.1 | `accessToken_isValidBeforeExpiry` | ✅ |
| 3.3.2 | `refreshTokenRotation_oldTokenIsRevoked` | ✅ |
| 3.3.3 | `loginResponse_cookieFlagsUnchanged` | ✅ |
| 3.3.4 | `logout_revokesAccessToken` | ✅ |
| 3.3.5 | `loginResponseBody_hasNoRefreshTokenField` | ✅ |
| 3.3.6 | `loginResponse_roleMatchesUserRole` | ✅ |

---

## 3.4 — RbacRegressionTest (10 tests)

| # | Test Method | ผล |
|---|-------------|-----|
| 3.4.1 | `createDevice_operator_returns403` | ✅ |
| 3.4.2 | `createDevice_admin_returns201` | ✅ |
| 3.4.3 | `patchLifecycle_operator_returns403` | ✅ |
| 3.4.4 | `patchFirmware_operator_returns403` | ✅ |
| 3.4.5 | `readDevices_operator_returns200` | ✅ |
| 3.4.6 | `readAlerts_operator_returns200` | ✅ |
| 3.4.7 | `acknowledgeAlert_operator_returns403` | ✅ |
| 3.4.8 | `acknowledgeAlert_admin_returns204` | ✅ |
| 3.4.9 | `generateEnrollmentToken_operator_returns403` | ✅ |
| 3.4.10 | `enrollEndpoint_withInvalidToken_returns400` | ✅ |

---

## 3.5 — MultiTenantRegressionTest (6 tests)

| # | Test Method | ผล |
|---|-------------|-----|
| 3.5.1 | `deviceList_foreignOrg_returnsEmpty` | ✅ |
| 3.5.2 | `deviceDetail_foreignOrg_returns404` | ✅ |
| 3.5.3 | `telemetry_foreignOrg_returns404` | ✅ |
| 3.5.4 | `alertList_foreignOrg_returnsEmpty` | ✅ |
| 3.5.5 | `rlsPolicies_existForAllTenantTables` | ✅ |
| 3.5.6 | `createdDevice_hasCorrectOrganizationId` | ✅ |

---

## 3.6 — MigrationRegressionTest (5 tests)

| # | Test Method | ผล |
|---|-------------|-----|
| 3.6.1 | `flywayMigrations_allAppliedSuccessfully` | ✅ |
| 3.6.2 | `seedUsers_surviveAllMigrations` | ✅ |
| 3.6.3 | `rowLevelSecurity_isEnabledOnTenantTables` | ✅ |
| 3.6.4 | `keyIndexes_stillPresentAfterMigration` | ✅ |
| 3.6.5 | `foreignKeyConstraints_intactAfterMigration` | ✅ |

---

## 3.7 — RateLimitRegressionTest (5 tests)

| # | Test Method | ผล |
|---|-------------|-----|
| 3.7.1 | `authEndpoint_11thRequest_returns429` | ✅ |
| 3.7.2 | `apiEndpoint_101stRequest_returns429` | ✅ |
| 3.7.3 | `actuatorPath_isExemptFromRateLimit` | ✅ |
| 3.7.4 | `rateLimitResponse_bodyIsJson_withErrorField` | ✅ |
| 3.7.5 | `devicesEnroll_usesAuthBucket_limitAt10` | ✅ |

---

## 3.8 — WebSocketRegressionTest (5 tests)

| # | Test Method | ผล |
|---|-------------|-----|
| 3.8.1 | `validToken_handshakeAccepted_orgIdSetInAttributes` | ✅ |
| 3.8.2 | `missingToken_handshakeRejected` | ✅ |
| 3.8.3 | `broadcastLocal_parsesOrgIdPipePayloadFormat` | ✅ |
| 3.8.4 | `broadcastLocal_doesNotDeliverCrossOrgMessages` | ✅ |
| 3.8.5 | `closedSession_isRemovedFromSessionSet_noBroadcastAfterClose` | ✅ |
