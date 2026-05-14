# Telemetry Retention — Sentinel IoT Platform

---

## Overview

Raw telemetry is retained for 30 days (configurable via `TELEMETRY_RETENTION_DAYS`). The retention cron runs at **02:30 daily** in three phases:

1. **Aggregate with late-arrival look-back** — `telemetry_hourly_aggregates` is upserted for the window `[today − lateArrivalLookbackDays, today)` (default: 2 days). Using `ON CONFLICT DO UPDATE` makes it idempotent, so late IoT messages are retroactively folded into the correct buckets on the next nightly run.
2. **Prune** — Raw rows older than the retention window are deleted from the partitioned `telemetry` table.
3. **Drop old partitions** — Empty monthly child tables (e.g. `telemetry_2025_01`) past the retention window are detached and dropped automatically, keeping the partition catalog from growing unbounded.

The dashboard's historical analytics mode uses the hourly aggregates for 24h and 7d windows, showing shaded min/max bands around the average line.

---

## Configuration

| Env var | Default | Description |
|---------|---------|-------------|
| `TELEMETRY_RETENTION_DAYS` | `30` | Raw telemetry retention window in days |
| `lateArrivalLookbackDays` | `2` | Look-back window for hourly aggregate upsert |

---

## Known Limitation

Monthly partition child tables are pre-created through `telemetry_2026_12`. Telemetry outside this range lands in `telemetry_default`. New year migrations must be added before the range is exhausted. See [tradeoffs.md](tradeoffs.md) for context.
