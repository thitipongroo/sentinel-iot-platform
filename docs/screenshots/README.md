# screenshots/ — Sentinel IoT Platform

Architecture diagrams และ visual assets ที่อ้างอิงจากเอกสารใน [system-design/](../system-design/)

---

## Architecture Diagrams

| ไฟล์ | คำอธิบาย |
|------|----------|
| [sentinel-high-level-diagram.png](sentinel-high-level-diagram.png) | High-level system diagram — ภาพรวม component ทั้งหมด |
| [sentinel-architecture-diagram.png](sentinel-architecture-diagram.png) | Detailed architecture diagram พร้อม internal data flows |
| [sentinel-tech-stack.png](sentinel-tech-stack.png) | Technology stack — layers และ frameworks ที่ใช้ |
| [sentinel-deployment-topology.png](sentinel-deployment-topology.png) | Deployment topology — Docker Compose services และ network |

---

## Data Flow Diagrams

| ไฟล์ | คำอธิบาย |
|------|----------|
| [sentinel-data-flow-normal-ingestion-path.png](sentinel-data-flow-normal-ingestion-path.png) | Data flow — happy path (telemetry ingestion → storage → broadcast) |
| [sentinel-data-flow-failure-ingestion-path.png](sentinel-data-flow-failure-ingestion-path.png) | Data flow — failure path (DB down → replay queue → recovery) |
| [sentinel-data-flow-normal-path.png](sentinel-data-flow-normal-path.png) | Detailed normal path flow diagram |
| [sentinel-data-flow-failure-path.png](sentinel-data-flow-failure-path.png) | Detailed failure path flow diagram |

---

## UI Screenshots

| ไฟล์ | คำอธิบาย |
|------|----------|
| [dashboard.png](dashboard.png) | Dashboard หน้าหลัก — real-time charts, device list, alert summary |
| [alerts.png](alerts.png) | Alerts page — unacknowledged alerts, acknowledge controls |
| [grafana.png](grafana.png) | Grafana dashboard — Prometheus metrics, SLO panels |
| [sentinel-project-structure.png](sentinel-project-structure.png) | Project directory structure overview |
