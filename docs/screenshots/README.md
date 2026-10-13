# screenshots/ — Sentinel IoT Platform

คลังภาพหน้าจอ UI จริงของระบบ Sentinel IoT Platform ที่ได้จากการทดสอบ End-to-End (E2E) ด้วย Cypress จัดโครงสร้างแยกตามเมนูการทำงานของระบบ (`docs/screenshots/[xx-menu]/...`)

---

## 📸 รายการภาพหน้าจอแยกตามเมนู (Menu Structure)

### 01. Login & Authentication (`01-login/`)
| ไฟล์ | รายละเอียดหน้าจอ |
|---|---|
| [login-page.png](01-login/login-page.png) | หน้าจอเข้าสู่ระบบ แสดง Branding, Username/Password form และ Forgot Password link |

---

### 02. Dashboard Overview (`02-dashboard/`)
| ไฟล์ | รายละเอียดหน้าจอ |
|---|---|
| [dashboard-overview.png](02-dashboard/dashboard-overview.png) | ภาพรวม Dashboard: StatsBar (Total, Online, Offline, Critical Alerts, Events/min, Buffered), Device Table, Telemetry Chart และ Alerts |
| [telemetry-smoke-tab.png](02-dashboard/telemetry-smoke-tab.png) | กราฟ Telemetry ขณะสลับมาที่แท็บ Smoke (ppm) และ Motion |
| [admin-device-management.png](02-dashboard/admin-device-management.png) | แผงควบคุม Device Management สำหรับ ADMIN (เปลี่ยนสถานะ Lifecycle, อัปเดตเวอร์ชัน Firmware แบบ SemVer) |

---

### 03. Device Management (`03-devices/`)
| ไฟล์ | รายละเอียดหน้าจอ |
|---|---|
| [devices-list.png](03-devices/devices-list.png) | หน้ารายการอุปกรณ์ (`/devices`) แสดงตารางอุปกรณ์, ตัวกรองค้นหา, ตัวกรองสถานะ Online/Offline, Lifecycle และปุ่ม Register Device |
| [device-detail.png](03-devices/device-detail.png) | หน้ารายละเอียดอุปกรณ์ (`/devices/:id`) แสดง Device Info Card, Firmware, Breadcrumbs, กราฟ และ Recent Alerts |

---

### 04. Digital Twin & Device Shadow (`04-digital-twin/`)
| ไฟล์ | รายละเอียดหน้าจอ |
|---|---|
| [digital-twin-hub.png](04-digital-twin/digital-twin-hub.png) | ศูนย์จัดการ Digital Twin Hub (`/digital-twin`) แสดง Reported / Desired State, Delta Resolution, OCC Versioning และ JSON Editor |

---

### 05. Predictive AI Analytics (`05-predictive-ai/`)
| ไฟล์ | รายละเอียดหน้าจอ |
|---|---|
| [predictive-ai-analytics.png](05-predictive-ai/predictive-ai-analytics.png) | หน้าวิเคราะห์ข้อมูล AI เชิงพยากรณ์ (`/predictive-ai`) แสดง Dynamic Baselines, Anomaly Detection Thresholds, Feature Contributions และ Inference Simulator |

---

### 06. Lakehouse Historical Analytics (`06-lakehouse/`)
| ไฟล์ | รายละเอียดหน้าจอ |
|---|---|
| [lakehouse-explorer.png](06-lakehouse/lakehouse-explorer.png) | หน้าระบบวิเคราะห์ข้อมูลย้อนหลัง DuckDB Lakehouse (`/lakehouse`) สอบถามข้อมูล Parquet ย้อนหลังหลายปีแบบ Sub-second Latency พร้อม Range Presets |

---

### 07. Alerts Management (`07-alerts/`)
| ไฟล์ | รายละเอียดหน้าจอ |
|---|---|
| [alerts-management.png](07-alerts/alerts-management.png) | หน้าจัดการแจ้งเตือน (`/alerts`) แสดงรายการแจ้งเตือน, ตัวกรองระดับ CRITICAL, และปุ่ม Acknowledge All |

---

### 08. User Management (`08-users/`)
| ไฟล์ | รายละเอียดหน้าจอ |
|---|---|
| [user-management.png](08-users/user-management.png) | หน้าจัดการผู้ใช้ (`/users`) แสดงรายการบัญชีผู้ใช้, สิทธิ์ ADMIN/OPERATOR, ป้าย `(you)` ป้องกันบัญชีตนเอง และปุ่ม Add/Delete User |

---

### 09. Platform Settings (`09-settings/`)
| ไฟล์ | รายละเอียดหน้าจอ |
|---|---|
| [platform-settings.png](09-settings/platform-settings.png) | หน้าตั้งค่าระบบ (`/settings`) แสดงเกณฑ์ขีดจำกัดเตือนภัย (Thresholds), วันจัดเก็บข้อมูล (Retention) และสถานะ Notification Channels |

---

### 10. System Resilience (`10-system/`)
| ไฟล์ | รายละเอียดหน้าจอ |
|---|---|
| [system-offline-banner.png](10-system/system-offline-banner.png) | แบนเนอร์จำลองกรณีเครือข่ายออฟไลน์ "You are offline" พร้อมการกู้คืนการเชื่อมต่อ |
