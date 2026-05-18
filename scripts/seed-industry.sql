-- ─────────────────────────────────────────────────────────────────────────────
-- Sentinel IoT Platform — Industry Device Catalog Seed
--
-- 8 industries × 47 devices, each with per-sensor capability thresholds (JSONB),
-- 48 h of hourly telemetry, hourly aggregates, and sample alerts.
--
-- Industry coverage:
--   1. Manufacturing / Smart Factory       8 devices
--   2. Cold Chain & Food Safety            6 devices
--   3. Data Center & IT Infrastructure     6 devices
--   4. Agriculture & Greenhouse            6 devices
--   5. Healthcare & Pharmaceuticals        5 devices
--   6. Energy & Utilities                  5 devices
--   7. Smart Building & Facilities         6 devices
--   8. Logistics & Warehouse               5 devices
--
-- Sensor types used (from SensorType enum):
--   TEMPERATURE, HUMIDITY, PRESSURE, SMOKE_PPM, CO2_PPM, CO_PPM, VOC_INDEX,
--   PM25, PM10, O3_PPB, MOTION, VIBRATION_G, TILT_DEG, VOLTAGE_V, CURRENT_A,
--   POWER_W, ENERGY_KWH, BATTERY_V, BATTERY_PCT, SIGNAL_RSSI, LIGHT_LUX,
--   UV_INDEX, SOUND_DB, WATER_LEVEL_PCT, FLOW_LPM, PH
--
-- UUID scheme:
--   Organizations : b[n]000000-0000-0000-0000-000000000000  (n = 1-8)
--   Admin users   : c[n]000000-0000-0000-0000-000000000000
--   Devices       : d[n]0000[kk]-0000-0000-0000-000000000000  (kk = 01-08)
--
-- Safe to re-run: ON CONFLICT DO NOTHING / DO UPDATE for all tables.
--
-- Run:
--   ./scripts/seed-industry.sh
--   docker exec -i sentinel-postgres psql -U sentinel -d sentinel \
--     < scripts/seed-industry.sql
-- ─────────────────────────────────────────────────────────────────────────────

\set ON_ERROR_STOP on
BEGIN;

-- ── 0. Cleanup previous run ───────────────────────────────────────────────────

DO $$
DECLARE
  seed_ids UUID[];
BEGIN
  SELECT ARRAY(
    SELECT id FROM devices
    WHERE name ~ '^(mfg|cold|dc|agri|health|energy|building|logistics)-'
  ) INTO seed_ids;

  IF array_length(seed_ids, 1) IS NOT NULL THEN
    DELETE FROM alerts                     WHERE device_id = ANY(seed_ids);
    DELETE FROM telemetry_hourly_aggregates WHERE device_id = ANY(seed_ids);
    DELETE FROM telemetry                  WHERE device_id = ANY(seed_ids);
    DELETE FROM devices                    WHERE id        = ANY(seed_ids);
  END IF;
END;
$$;

-- ── 1. Organizations ──────────────────────────────────────────────────────────

INSERT INTO organizations (id, slug, name) VALUES
  ('b1000000-0000-0000-0000-000000000000', 'manufacturing',  'Acme Manufacturing Co.'),
  ('b2000000-0000-0000-0000-000000000000', 'cold-chain',     'FreshLink Cold Chain Logistics'),
  ('b3000000-0000-0000-0000-000000000000', 'datacenter',     'EdgeVault Data Center'),
  ('b4000000-0000-0000-0000-000000000000', 'agriculture',    'GreenField Agritech'),
  ('b5000000-0000-0000-0000-000000000000', 'healthcare',     'MedTech Pharma Solutions'),
  ('b6000000-0000-0000-0000-000000000000', 'energy',         'SunGrid Energy Systems'),
  ('b7000000-0000-0000-0000-000000000000', 'smart-building', 'Apex Smart Facilities'),
  ('b8000000-0000-0000-0000-000000000000', 'logistics',      'SwiftMove Logistics')
ON CONFLICT (slug) DO NOTHING;

-- ── 2. Admin users (password: sentinel123 — BCrypt-hashed via pgcrypto) ───────

INSERT INTO app_users (id, username, password, role, organization_id) VALUES
  ('c1000000-0000-0000-0000-000000000000', 'org-manufacturing-admin',  crypt('sentinel123', gen_salt('bf', 10)), 'ADMIN', 'b1000000-0000-0000-0000-000000000000'),
  ('c2000000-0000-0000-0000-000000000000', 'org-cold-chain-admin',     crypt('sentinel123', gen_salt('bf', 10)), 'ADMIN', 'b2000000-0000-0000-0000-000000000000'),
  ('c3000000-0000-0000-0000-000000000000', 'org-datacenter-admin',     crypt('sentinel123', gen_salt('bf', 10)), 'ADMIN', 'b3000000-0000-0000-0000-000000000000'),
  ('c4000000-0000-0000-0000-000000000000', 'org-agriculture-admin',    crypt('sentinel123', gen_salt('bf', 10)), 'ADMIN', 'b4000000-0000-0000-0000-000000000000'),
  ('c5000000-0000-0000-0000-000000000000', 'org-healthcare-admin',     crypt('sentinel123', gen_salt('bf', 10)), 'ADMIN', 'b5000000-0000-0000-0000-000000000000'),
  ('c6000000-0000-0000-0000-000000000000', 'org-energy-admin',         crypt('sentinel123', gen_salt('bf', 10)), 'ADMIN', 'b6000000-0000-0000-0000-000000000000'),
  ('c7000000-0000-0000-0000-000000000000', 'org-smart-building-admin', crypt('sentinel123', gen_salt('bf', 10)), 'ADMIN', 'b7000000-0000-0000-0000-000000000000'),
  ('c8000000-0000-0000-0000-000000000000', 'org-logistics-admin',      crypt('sentinel123', gen_salt('bf', 10)), 'ADMIN', 'b8000000-0000-0000-0000-000000000000')
ON CONFLICT (username) DO NOTHING;

-- ── 3. Devices ────────────────────────────────────────────────────────────────
-- Capabilities JSONB keys match SensorCapability record field names (camelCase,
-- deserialized by DeviceCapabilitiesConverter via plain ObjectMapper).

INSERT INTO devices (id, name, status, description, location,
                     organization_id, lifecycle_status, firmware_version,
                     created_at, last_seen, capabilities)
VALUES

-- ── 3.1 Manufacturing / Smart Factory ─────────────────────────────────────────

('d1000001-0000-0000-0000-000000000000',
 'mfg-assembly-line-01', 'ONLINE',
 'Assembly Line Temperature, Vibration & Fume Monitor',
 'Plant A — Assembly Line 1',
 'b1000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '120 days', NOW() - INTERVAL '3 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-40.0,"maxOperational":200.0,"warnThreshold":78.0,"critThreshold":85.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":20.0,"warnThreshold":4.0,"critThreshold":7.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"SMOKE_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":1000.0,"warnThreshold":150.0,"critThreshold":250.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d1000002-0000-0000-0000-000000000000',
 'mfg-cnc-machine-01', 'ONLINE',
 'CNC Machine Health Monitor — Predictive Maintenance',
 'Machining Centre — Bay 3',
 'b1000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '90 days', NOW() - INTERVAL '2 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":150.0,"warnThreshold":65.0,"critThreshold":78.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":20.0,"warnThreshold":3.5,"critThreshold":6.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"SOUND_DB":{"unit":"dB","minOperational":0.0,"maxOperational":140.0,"warnThreshold":90.0,"critThreshold":105.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CURRENT_A":{"unit":"A","minOperational":0.0,"maxOperational":100.0,"warnThreshold":42.0,"critThreshold":48.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d1000003-0000-0000-0000-000000000000',
 'mfg-air-quality-01', 'ONLINE',
 'Factory Floor Multi-Gas Air Quality Station',
 'Plant A — Central Monitoring Post',
 'b1000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '60 days', NOW() - INTERVAL '4 minutes',
 '{"CO2_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":50000.0,"warnThreshold":1000.0,"critThreshold":2000.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"CO_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":1000.0,"warnThreshold":20.0,"critThreshold":35.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VOC_INDEX":{"unit":"idx","minOperational":0.0,"maxOperational":500.0,"warnThreshold":200.0,"critThreshold":350.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"PM25":{"unit":"µg/m³","minOperational":0.0,"maxOperational":500.0,"warnThreshold":35.0,"critThreshold":75.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d1000004-0000-0000-0000-000000000000',
 'mfg-motor-drive-01', 'ONLINE',
 'Motor Drive Electrical Health & Power Monitor',
 'Plant B — Drive Room',
 'b1000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '75 days', NOW() - INTERVAL '2 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":120.0,"warnThreshold":75.0,"critThreshold":85.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CURRENT_A":{"unit":"A","minOperational":0.0,"maxOperational":150.0,"warnThreshold":85.0,"critThreshold":95.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VOLTAGE_V":{"unit":"V","minOperational":0.0,"maxOperational":600.0,"warnThreshold":450.0,"critThreshold":490.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"POWER_W":{"unit":"W","minOperational":0.0,"maxOperational":60000.0,"warnThreshold":42000.0,"critThreshold":48000.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

('d1000005-0000-0000-0000-000000000000',
 'mfg-compressor-01', 'ONLINE',
 'Industrial Air Compressor Station — Pressure & Health',
 'Utility Block — Compressor Room',
 'b1000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '85 days', NOW() - INTERVAL '5 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":120.0,"warnThreshold":70.0,"critThreshold":80.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"PRESSURE":{"unit":"kPa","minOperational":0.0,"maxOperational":2000.0,"warnThreshold":900.0,"critThreshold":1100.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":15.0,"warnThreshold":3.0,"critThreshold":4.5,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"CURRENT_A":{"unit":"A","minOperational":0.0,"maxOperational":50.0,"warnThreshold":25.0,"critThreshold":29.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d1000006-0000-0000-0000-000000000000',
 'mfg-welding-01', 'ONLINE',
 'Welding Bay Fume & Safety Monitor',
 'Fabrication Shop — Welding Bay 2',
 'b1000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '55 days', NOW() - INTERVAL '6 minutes',
 '{"SMOKE_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":1000.0,"warnThreshold":200.0,"critThreshold":400.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CO_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":1000.0,"warnThreshold":50.0,"critThreshold":80.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VOC_INDEX":{"unit":"idx","minOperational":0.0,"maxOperational":500.0,"warnThreshold":250.0,"critThreshold":400.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

('d1000007-0000-0000-0000-000000000000',
 'mfg-conveyor-01', 'ONLINE',
 'Conveyor Belt Motion & Mechanical Health Monitor',
 'Plant A — Packaging Conveyor C1',
 'b1000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '100 days', NOW() - INTERVAL '3 minutes',
 '{"MOTION":{"unit":"bool","minOperational":0.0,"maxOperational":1.0,"warnThreshold":null,"critThreshold":null,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":10.0,"warnThreshold":2.5,"critThreshold":4.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":100.0,"warnThreshold":58.0,"critThreshold":65.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d1000008-0000-0000-0000-000000000000',
 'mfg-utility-room-01', 'ONLINE',
 'Plant Utility Room Environmental Monitor',
 'Utility Block — Main Control Room',
 'b1000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '110 days', NOW() - INTERVAL '4 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":60.0,"warnThreshold":32.0,"critThreshold":38.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":70.0,"critThreshold":85.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CO2_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":5000.0,"warnThreshold":1000.0,"critThreshold":1500.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

-- ── 3.2 Cold Chain & Food Safety ──────────────────────────────────────────────

('d2000001-0000-0000-0000-000000000000',
 'cold-blast-freezer-01', 'ONLINE',
 'Industrial Blast Freezer — Deep Freeze (-18°C)',
 'Frozen Storage — Bay F1',
 'b2000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '180 days', NOW() - INTERVAL '2 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-50.0,"maxOperational":10.0,"warnThreshold":-15.0,"critThreshold":-10.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":95.0,"critThreshold":99.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d2000002-0000-0000-0000-000000000000',
 'cold-walkin-fridge-01', 'ONLINE',
 'Walk-in Refrigerator Unit — Chilled Storage +2 to +4°C',
 'Chilled Storage — Bay C2',
 'b2000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '150 days', NOW() - INTERVAL '3 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-10.0,"maxOperational":15.0,"warnThreshold":6.0,"critThreshold":8.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":92.0,"critThreshold":96.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d2000003-0000-0000-0000-000000000000',
 'cold-display-case-01', 'ONLINE',
 'Refrigerated Retail Display Case Monitor',
 'Retail Floor — Aisle 4 Display',
 'b2000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '60 days', NOW() - INTERVAL '5 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-5.0,"maxOperational":15.0,"warnThreshold":6.0,"critThreshold":8.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":88.0,"critThreshold":93.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d2000004-0000-0000-0000-000000000000',
 'cold-pasteuriser-01', 'ONLINE',
 'HTST Pasteurisation Line Temperature Monitor (72°C/15s)',
 'Processing Plant — HTST Line 1',
 'b2000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '90 days', NOW() - INTERVAL '2 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":60.0,"maxOperational":95.0,"warnThreshold":73.0,"critThreshold":72.0,"thresholdDirection":"BELOW","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":65.0,"critThreshold":70.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"PRESSURE":{"unit":"kPa","minOperational":0.0,"maxOperational":600.0,"warnThreshold":350.0,"critThreshold":390.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

('d2000005-0000-0000-0000-000000000000',
 'cold-transport-01', 'ONLINE',
 'Refrigerated Vehicle Fleet Temperature Tracker',
 'Fleet — Refrigerated Truck TK-007',
 'b2000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '45 days', NOW() - INTERVAL '8 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-30.0,"maxOperational":20.0,"warnThreshold":4.0,"critThreshold":7.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":85.0,"critThreshold":92.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":15.0,"warnThreshold":4.5,"critThreshold":7.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2}}'::jsonb),

('d2000006-0000-0000-0000-000000000000',
 'cold-wine-cellar-01', 'ONLINE',
 'Wine Cellar Precision Climate Monitor (12°C / 65%RH)',
 'Storage — Wine Cellar Wing',
 'b2000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '200 days', NOW() - INTERVAL '4 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":8.0,"maxOperational":25.0,"warnThreshold":15.0,"critThreshold":17.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":75.0,"critThreshold":78.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"LIGHT_LUX":{"unit":"lux","minOperational":0.0,"maxOperational":10000.0,"warnThreshold":100.0,"critThreshold":200.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

-- ── 3.3 Data Center & IT Infrastructure ──────────────────────────────────────

('d3000001-0000-0000-0000-000000000000',
 'dc-server-rack-01', 'ONLINE',
 'Server Rack Thermal Monitor — Hot Aisle Containment',
 'Data Hall 1 — Row A, Rack A03',
 'b3000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '365 days', NOW() - INTERVAL '1 minute',
 '{"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":80.0,"warnThreshold":35.0,"critThreshold":40.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":55.0,"critThreshold":60.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d3000002-0000-0000-0000-000000000000',
 'dc-ups-01', 'ONLINE',
 'UPS Battery System Monitor — 48V DC Bus',
 'Power Room — UPS Bank 1',
 'b3000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '300 days', NOW() - INTERVAL '2 minutes',
 '{"BATTERY_PCT":{"unit":"%","minOperational":0.0,"maxOperational":100.0,"warnThreshold":25.0,"critThreshold":10.0,"thresholdDirection":"BELOW","enabled":true,"decimalPlaces":0},"BATTERY_V":{"unit":"V","minOperational":0.0,"maxOperational":60.0,"warnThreshold":46.0,"critThreshold":44.0,"thresholdDirection":"BELOW","enabled":true,"decimalPlaces":1},"CURRENT_A":{"unit":"A","minOperational":0.0,"maxOperational":150.0,"warnThreshold":90.0,"critThreshold":98.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":60.0,"warnThreshold":42.0,"critThreshold":46.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d3000003-0000-0000-0000-000000000000',
 'dc-pdu-01', 'ONLINE',
 'Smart Power Distribution Unit (PDU) — 3-Phase 30kW',
 'Data Hall 1 — PDU 03',
 'b3000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '300 days', NOW() - INTERVAL '1 minute',
 '{"POWER_W":{"unit":"W","minOperational":0.0,"maxOperational":35000.0,"warnThreshold":24000.0,"critThreshold":28500.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"CURRENT_A":{"unit":"A","minOperational":0.0,"maxOperational":150.0,"warnThreshold":110.0,"critThreshold":125.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VOLTAGE_V":{"unit":"V","minOperational":0.0,"maxOperational":260.0,"warnThreshold":212.0,"critThreshold":208.0,"thresholdDirection":"BELOW","enabled":true,"decimalPlaces":0},"ENERGY_KWH":{"unit":"kWh","minOperational":0.0,"maxOperational":999999.0,"warnThreshold":null,"critThreshold":null,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2}}'::jsonb),

('d3000004-0000-0000-0000-000000000000',
 'dc-leak-sensor-01', 'ONLINE',
 'Raised-Floor Water Leak Detector',
 'Data Hall 1 — Raised Floor Grid B7',
 'b3000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '250 days', NOW() - INTERVAL '3 minutes',
 '{"WATER_LEVEL_PCT":{"unit":"%","minOperational":0.0,"maxOperational":100.0,"warnThreshold":1.0,"critThreshold":5.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":50.0,"warnThreshold":28.0,"critThreshold":30.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d3000005-0000-0000-0000-000000000000',
 'dc-crac-unit-01', 'ONLINE',
 'Computer Room Air Conditioning (CRAC) Unit',
 'Data Hall 1 — CRAC Unit East Wall',
 'b3000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '365 days', NOW() - INTERVAL '2 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":50.0,"warnThreshold":26.0,"critThreshold":28.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":60.0,"critThreshold":65.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"PRESSURE":{"unit":"hPa","minOperational":900.0,"maxOperational":1100.0,"warnThreshold":1020.0,"critThreshold":1023.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

('d3000006-0000-0000-0000-000000000000',
 'dc-generator-01', 'ONLINE',
 'Emergency Diesel Generator Health Monitor',
 'Plant Room — Generator Bay',
 'b3000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '400 days', NOW() - INTERVAL '10 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":120.0,"warnThreshold":85.0,"critThreshold":93.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":15.0,"warnThreshold":4.0,"critThreshold":6.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"CURRENT_A":{"unit":"A","minOperational":0.0,"maxOperational":350.0,"warnThreshold":210.0,"critThreshold":240.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VOLTAGE_V":{"unit":"V","minOperational":0.0,"maxOperational":280.0,"warnThreshold":210.0,"critThreshold":205.0,"thresholdDirection":"BELOW","enabled":true,"decimalPlaces":0}}'::jsonb),

-- ── 3.4 Agriculture & Greenhouse ──────────────────────────────────────────────

('d4000001-0000-0000-0000-000000000000',
 'agri-greenhouse-01', 'ONLINE',
 'Hydroponic Greenhouse Climate Controller',
 'Greenhouse Block G1',
 'b4000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '200 days', NOW() - INTERVAL '4 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-10.0,"maxOperational":50.0,"warnThreshold":32.0,"critThreshold":36.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":90.0,"critThreshold":94.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CO2_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":5000.0,"warnThreshold":1800.0,"critThreshold":1950.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"LIGHT_LUX":{"unit":"lux","minOperational":0.0,"maxOperational":120000.0,"warnThreshold":85000.0,"critThreshold":95000.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

('d4000002-0000-0000-0000-000000000000',
 'agri-soil-01', 'ONLINE',
 'Smart Soil Multi-Parameter Sensor',
 'Field Zone F3 — Row 14',
 'b4000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '90 days', NOW() - INTERVAL '15 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-10.0,"maxOperational":60.0,"warnThreshold":30.0,"critThreshold":35.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":75.0,"critThreshold":82.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"PH":{"unit":"pH","minOperational":0.0,"maxOperational":14.0,"warnThreshold":7.5,"critThreshold":8.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2}}'::jsonb),

('d4000003-0000-0000-0000-000000000000',
 'agri-irrigation-01', 'ONLINE',
 'Smart Irrigation Flow & Pressure Monitor',
 'Irrigation Pump Station — Zone C',
 'b4000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '120 days', NOW() - INTERVAL '5 minutes',
 '{"FLOW_LPM":{"unit":"L/min","minOperational":0.0,"maxOperational":1000.0,"warnThreshold":720.0,"critThreshold":780.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"PRESSURE":{"unit":"kPa","minOperational":0.0,"maxOperational":800.0,"warnThreshold":500.0,"critThreshold":580.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"WATER_LEVEL_PCT":{"unit":"%","minOperational":0.0,"maxOperational":100.0,"warnThreshold":15.0,"critThreshold":8.0,"thresholdDirection":"BELOW","enabled":true,"decimalPlaces":1}}'::jsonb),

('d4000004-0000-0000-0000-000000000000',
 'agri-weather-01', 'ONLINE',
 'Precision Agriculture Outdoor Weather Station',
 'Field Boundary — Station WX-01',
 'b4000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '150 days', NOW() - INTERVAL '10 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-40.0,"maxOperational":60.0,"warnThreshold":38.0,"critThreshold":44.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":92.0,"critThreshold":97.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"PRESSURE":{"unit":"hPa","minOperational":800.0,"maxOperational":1100.0,"warnThreshold":960.0,"critThreshold":950.0,"thresholdDirection":"BELOW","enabled":true,"decimalPlaces":0},"UV_INDEX":{"unit":"idx","minOperational":0.0,"maxOperational":15.0,"warnThreshold":8.0,"critThreshold":10.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"LIGHT_LUX":{"unit":"lux","minOperational":0.0,"maxOperational":150000.0,"warnThreshold":100000.0,"critThreshold":115000.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

('d4000005-0000-0000-0000-000000000000',
 'agri-silo-01', 'ONLINE',
 'Grain Silo Condition Monitor — Hotspot Detection',
 'Storage Silo S2 — Level 3',
 'b4000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '180 days', NOW() - INTERVAL '20 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-10.0,"maxOperational":70.0,"warnThreshold":28.0,"critThreshold":35.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":68.0,"critThreshold":75.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CO2_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":10000.0,"warnThreshold":1500.0,"critThreshold":3000.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

('d4000006-0000-0000-0000-000000000000',
 'agri-livestock-01', 'ONLINE',
 'Poultry House Environment Controller',
 'Poultry Unit P4 — House B',
 'b4000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '95 days', NOW() - INTERVAL '5 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":50.0,"warnThreshold":32.0,"critThreshold":34.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":75.0,"critThreshold":80.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CO2_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":5000.0,"warnThreshold":2000.0,"critThreshold":3000.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"VOC_INDEX":{"unit":"idx","minOperational":0.0,"maxOperational":500.0,"warnThreshold":200.0,"critThreshold":350.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

-- ── 3.5 Healthcare & Pharmaceuticals ─────────────────────────────────────────

('d5000001-0000-0000-0000-000000000000',
 'health-vaccine-fridge-01', 'ONLINE',
 'WHO-PQS Vaccine Storage Refrigerator (+2 to +8°C)',
 'Pharmacy Dispensary — Cold Room A',
 'b5000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '500 days', NOW() - INTERVAL '1 minute',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-5.0,"maxOperational":15.0,"warnThreshold":7.0,"critThreshold":8.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":70.0,"critThreshold":75.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d5000002-0000-0000-0000-000000000000',
 'health-clean-room-01', 'ONLINE',
 'ISO Class 7 Clean Room Environment Monitor',
 'Manufacturing Suite — Clean Room CR-02',
 'b5000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '400 days', NOW() - INTERVAL '2 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":15.0,"maxOperational":30.0,"warnThreshold":22.0,"critThreshold":23.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":62.0,"critThreshold":65.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"PRESSURE":{"unit":"hPa","minOperational":1000.0,"maxOperational":1030.0,"warnThreshold":1020.0,"critThreshold":1022.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"PM25":{"unit":"µg/m³","minOperational":0.0,"maxOperational":100.0,"warnThreshold":5.0,"critThreshold":10.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"PM10":{"unit":"µg/m³","minOperational":0.0,"maxOperational":200.0,"warnThreshold":10.0,"critThreshold":20.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2}}'::jsonb),

('d5000003-0000-0000-0000-000000000000',
 'health-autoclave-01', 'ONLINE',
 'Steam Autoclave Sterilization Monitor — 134°C Cycle',
 'CSSD — Autoclave Unit AU-3',
 'b5000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '300 days', NOW() - INTERVAL '6 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":80.0,"maxOperational":150.0,"warnThreshold":135.0,"critThreshold":138.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"PRESSURE":{"unit":"kPa","minOperational":0.0,"maxOperational":400.0,"warnThreshold":300.0,"critThreshold":340.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

('d5000004-0000-0000-0000-000000000000',
 'health-lab-gas-01', 'ONLINE',
 'Laboratory Chemical Fume & Gas Safety Monitor',
 'Research Lab — Fume Cabinet Zone',
 'b5000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '200 days', NOW() - INTERVAL '3 minutes',
 '{"CO_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":1000.0,"warnThreshold":25.0,"critThreshold":50.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"O3_PPB":{"unit":"ppb","minOperational":0.0,"maxOperational":500.0,"warnThreshold":50.0,"critThreshold":100.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"VOC_INDEX":{"unit":"idx","minOperational":0.0,"maxOperational":500.0,"warnThreshold":100.0,"critThreshold":200.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"CO2_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":10000.0,"warnThreshold":1500.0,"critThreshold":2500.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

('d5000005-0000-0000-0000-000000000000',
 'health-operating-room-01', 'ONLINE',
 'Operating Theatre HVAC & Air Quality Monitor',
 'Surgical Suite — OR-05',
 'b5000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '450 days', NOW() - INTERVAL '1 minute',
 '{"TEMPERATURE":{"unit":"°C","minOperational":15.0,"maxOperational":30.0,"warnThreshold":24.0,"critThreshold":25.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":60.0,"critThreshold":62.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"PRESSURE":{"unit":"hPa","minOperational":1000.0,"maxOperational":1030.0,"warnThreshold":1020.0,"critThreshold":1023.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"PM25":{"unit":"µg/m³","minOperational":0.0,"maxOperational":50.0,"warnThreshold":2.0,"critThreshold":5.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"CO2_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":5000.0,"warnThreshold":800.0,"critThreshold":1000.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

-- ── 3.6 Energy & Utilities ────────────────────────────────────────────────────

('d6000001-0000-0000-0000-000000000000',
 'energy-solar-inverter-01', 'ONLINE',
 'Grid-Tied Solar PV String Inverter — 10kW',
 'Rooftop Array — Inverter INV-3',
 'b6000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '180 days', NOW() - INTERVAL '3 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-20.0,"maxOperational":90.0,"warnThreshold":70.0,"critThreshold":78.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VOLTAGE_V":{"unit":"V","minOperational":0.0,"maxOperational":1100.0,"warnThreshold":980.0,"critThreshold":995.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"CURRENT_A":{"unit":"A","minOperational":0.0,"maxOperational":25.0,"warnThreshold":18.0,"critThreshold":19.5,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"POWER_W":{"unit":"W","minOperational":0.0,"maxOperational":12000.0,"warnThreshold":11800.0,"critThreshold":11950.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"ENERGY_KWH":{"unit":"kWh","minOperational":0.0,"maxOperational":999999.0,"warnThreshold":null,"critThreshold":null,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2}}'::jsonb),

('d6000002-0000-0000-0000-000000000000',
 'energy-smart-meter-01', 'ONLINE',
 'Industrial 3-Phase Smart Energy Meter',
 'Substation — Feeder Panel F2',
 'b6000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '400 days', NOW() - INTERVAL '1 minute',
 '{"POWER_W":{"unit":"W","minOperational":0.0,"maxOperational":70000.0,"warnThreshold":55000.0,"critThreshold":59000.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"CURRENT_A":{"unit":"A","minOperational":0.0,"maxOperational":200.0,"warnThreshold":135.0,"critThreshold":148.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VOLTAGE_V":{"unit":"V","minOperational":0.0,"maxOperational":500.0,"warnThreshold":460.0,"critThreshold":490.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"ENERGY_KWH":{"unit":"kWh","minOperational":0.0,"maxOperational":9999999.0,"warnThreshold":null,"critThreshold":null,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2}}'::jsonb),

('d6000003-0000-0000-0000-000000000000',
 'energy-transformer-01', 'ONLINE',
 'Distribution Transformer Health Monitor — 33kV/11kV',
 'Substation — Transformer Bay T2',
 'b6000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '500 days', NOW() - INTERVAL '5 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-20.0,"maxOperational":120.0,"warnThreshold":80.0,"critThreshold":90.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CURRENT_A":{"unit":"A","minOperational":0.0,"maxOperational":600.0,"warnThreshold":450.0,"critThreshold":490.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":10.0,"warnThreshold":2.5,"critThreshold":4.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2}}'::jsonb),

('d6000004-0000-0000-0000-000000000000',
 'energy-water-plant-01', 'ONLINE',
 'Water Treatment Plant Process Monitor',
 'Water Treatment Works — Dosing Station',
 'b6000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '200 days', NOW() - INTERVAL '5 minutes',
 '{"PH":{"unit":"pH","minOperational":0.0,"maxOperational":14.0,"warnThreshold":8.5,"critThreshold":9.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"FLOW_LPM":{"unit":"L/min","minOperational":0.0,"maxOperational":4000.0,"warnThreshold":2800.0,"critThreshold":2950.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"WATER_LEVEL_PCT":{"unit":"%","minOperational":0.0,"maxOperational":100.0,"warnThreshold":15.0,"critThreshold":8.0,"thresholdDirection":"BELOW","enabled":true,"decimalPlaces":1},"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":40.0,"warnThreshold":30.0,"critThreshold":33.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d6000005-0000-0000-0000-000000000000',
 'energy-wind-turbine-01', 'ONLINE',
 'Wind Turbine Drivetrain Health Monitor — 2MW',
 'Wind Farm — Turbine WT-07',
 'b6000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '250 days', NOW() - INTERVAL '4 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-20.0,"maxOperational":100.0,"warnThreshold":72.0,"critThreshold":82.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":15.0,"warnThreshold":5.0,"critThreshold":8.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"SOUND_DB":{"unit":"dB","minOperational":0.0,"maxOperational":130.0,"warnThreshold":90.0,"critThreshold":100.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CURRENT_A":{"unit":"A","minOperational":0.0,"maxOperational":400.0,"warnThreshold":260.0,"critThreshold":290.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

-- ── 3.7 Smart Building & Facilities ──────────────────────────────────────────

('d7000001-0000-0000-0000-000000000000',
 'building-hvac-01', 'ONLINE',
 'Central HVAC Air Handling Unit — AHU-3 (Demand Control Ventilation)',
 'Main Building — Level 2 Plant Room',
 'b7000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '300 days', NOW() - INTERVAL '2 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":50.0,"warnThreshold":26.0,"critThreshold":28.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":65.0,"critThreshold":70.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CO2_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":5000.0,"warnThreshold":1000.0,"critThreshold":1500.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"PRESSURE":{"unit":"hPa","minOperational":900.0,"maxOperational":1100.0,"warnThreshold":1025.0,"critThreshold":1028.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

('d7000002-0000-0000-0000-000000000000',
 'building-fire-alarm-01', 'ONLINE',
 'Addressable Fire Safety Sensor — Heat + Smoke + CO',
 'Level 3 — Corridor C Block',
 'b7000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '500 days', NOW() - INTERVAL '1 minute',
 '{"SMOKE_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":2000.0,"warnThreshold":300.0,"critThreshold":600.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CO_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":1000.0,"warnThreshold":50.0,"critThreshold":100.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":100.0,"warnThreshold":55.0,"critThreshold":70.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d7000003-0000-0000-0000-000000000000',
 'building-occupancy-01', 'ONLINE',
 'Smart Occupancy & Comfort Sensor — HVAC Demand Control',
 'Office Floor 4 — Open Plan Zone B',
 'b7000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '200 days', NOW() - INTERVAL '3 minutes',
 '{"MOTION":{"unit":"bool","minOperational":0.0,"maxOperational":1.0,"warnThreshold":null,"critThreshold":null,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"CO2_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":5000.0,"warnThreshold":1200.0,"critThreshold":1800.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"LIGHT_LUX":{"unit":"lux","minOperational":0.0,"maxOperational":5000.0,"warnThreshold":2500.0,"critThreshold":2800.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"SOUND_DB":{"unit":"dB","minOperational":0.0,"maxOperational":130.0,"warnThreshold":80.0,"critThreshold":90.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d7000004-0000-0000-0000-000000000000',
 'building-elevator-01', 'ONLINE',
 'Elevator Machine Room Monitor — Overheating Prevention',
 'Core — Elevator Machine Room',
 'b7000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '400 days', NOW() - INTERVAL '3 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":70.0,"warnThreshold":40.0,"critThreshold":50.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":15.0,"warnThreshold":4.0,"critThreshold":6.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"CURRENT_A":{"unit":"A","minOperational":0.0,"maxOperational":100.0,"warnThreshold":68.0,"critThreshold":78.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d7000005-0000-0000-0000-000000000000',
 'building-parking-01', 'ONLINE',
 'Basement Car Park Air Quality Monitor — CO Management',
 'Basement B2 — Parking Level P2',
 'b7000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '250 days', NOW() - INTERVAL '4 minutes',
 '{"CO_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":500.0,"warnThreshold":25.0,"critThreshold":50.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CO2_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":10000.0,"warnThreshold":2000.0,"critThreshold":3000.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"PM25":{"unit":"µg/m³","minOperational":0.0,"maxOperational":500.0,"warnThreshold":50.0,"critThreshold":100.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d7000006-0000-0000-0000-000000000000',
 'building-water-tank-01', 'ONLINE',
 'Roof Water Tank Level & Temperature Monitor — Legionella Risk',
 'Roof — Main Water Tank R1',
 'b7000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '350 days', NOW() - INTERVAL '10 minutes',
 '{"WATER_LEVEL_PCT":{"unit":"%","minOperational":0.0,"maxOperational":100.0,"warnThreshold":20.0,"critThreshold":10.0,"thresholdDirection":"BELOW","enabled":true,"decimalPlaces":1},"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":80.0,"warnThreshold":60.0,"critThreshold":65.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"PRESSURE":{"unit":"kPa","minOperational":0.0,"maxOperational":800.0,"warnThreshold":550.0,"critThreshold":580.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

-- ── 3.8 Logistics & Warehouse ─────────────────────────────────────────────────

('d8000001-0000-0000-0000-000000000000',
 'logistics-cold-truck-01', 'ONLINE',
 'Cold Chain Refrigerated Transport Monitor',
 'Fleet — Refrigerated Truck TK-014',
 'b8000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '60 days', NOW() - INTERVAL '12 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-30.0,"maxOperational":20.0,"warnThreshold":3.0,"critThreshold":6.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":85.0,"critThreshold":92.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":15.0,"warnThreshold":4.5,"critThreshold":7.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2}}'::jsonb),

('d8000002-0000-0000-0000-000000000000',
 'logistics-forklift-01', 'ONLINE',
 'Electric Forklift Battery & Health Monitor',
 'Warehouse Bay 3 — Forklift Fleet',
 'b8000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '90 days', NOW() - INTERVAL '5 minutes',
 '{"BATTERY_PCT":{"unit":"%","minOperational":0.0,"maxOperational":100.0,"warnThreshold":25.0,"critThreshold":10.0,"thresholdDirection":"BELOW","enabled":true,"decimalPlaces":0},"BATTERY_V":{"unit":"V","minOperational":0.0,"maxOperational":100.0,"warnThreshold":60.0,"critThreshold":55.0,"thresholdDirection":"BELOW","enabled":true,"decimalPlaces":1},"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":15.0,"warnThreshold":5.0,"critThreshold":7.5,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"TEMPERATURE":{"unit":"°C","minOperational":0.0,"maxOperational":80.0,"warnThreshold":55.0,"critThreshold":65.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d8000003-0000-0000-0000-000000000000',
 'logistics-warehouse-air-01', 'ONLINE',
 'High-Bay Warehouse Air Quality Monitor — Forklift CO',
 'Warehouse — Central Bay Mezzanine',
 'b8000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '120 days', NOW() - INTERVAL '3 minutes',
 '{"CO2_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":5000.0,"warnThreshold":1500.0,"critThreshold":2500.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"CO_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":500.0,"warnThreshold":20.0,"critThreshold":35.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"VOC_INDEX":{"unit":"idx","minOperational":0.0,"maxOperational":500.0,"warnThreshold":150.0,"critThreshold":300.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0},"TEMPERATURE":{"unit":"°C","minOperational":-10.0,"maxOperational":50.0,"warnThreshold":35.0,"critThreshold":38.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb),

('d8000004-0000-0000-0000-000000000000',
 'logistics-loading-dock-01', 'ONLINE',
 'Loading Dock Environmental & Security Monitor',
 'Loading Bay — Dock D4',
 'b8000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '80 days', NOW() - INTERVAL '6 minutes',
 '{"TEMPERATURE":{"unit":"°C","minOperational":-20.0,"maxOperational":50.0,"warnThreshold":35.0,"critThreshold":38.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"HUMIDITY":{"unit":"%RH","minOperational":0.0,"maxOperational":100.0,"warnThreshold":85.0,"critThreshold":92.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"CO_PPM":{"unit":"ppm","minOperational":0.0,"maxOperational":500.0,"warnThreshold":20.0,"critThreshold":35.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1},"MOTION":{"unit":"bool","minOperational":0.0,"maxOperational":1.0,"warnThreshold":null,"critThreshold":null,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":0}}'::jsonb),

('d8000005-0000-0000-0000-000000000000',
 'logistics-racking-01', 'ONLINE',
 'Automated Storage Racking Structural Integrity Monitor',
 'High-Bay Rack — Row 15, Level 8',
 'b8000000-0000-0000-0000-000000000000', 'ACTIVE', '3.2.0',
 NOW() - INTERVAL '150 days', NOW() - INTERVAL '8 minutes',
 '{"VIBRATION_G":{"unit":"g","minOperational":0.0,"maxOperational":10.0,"warnThreshold":1.5,"critThreshold":2.5,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"TILT_DEG":{"unit":"°","minOperational":-30.0,"maxOperational":30.0,"warnThreshold":3.0,"critThreshold":5.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":2},"TEMPERATURE":{"unit":"°C","minOperational":-10.0,"maxOperational":50.0,"warnThreshold":35.0,"critThreshold":38.0,"thresholdDirection":"ABOVE","enabled":true,"decimalPlaces":1}}'::jsonb)

ON CONFLICT (id) DO UPDATE SET
  status           = EXCLUDED.status,
  last_seen        = EXCLUDED.last_seen,
  capabilities     = EXCLUDED.capabilities,
  firmware_version = EXCLUDED.firmware_version;

-- ── 4. Telemetry (48 h of hourly readings per device) ─────────────────────────
--
-- device_params columns:
--   device_id, temp_base, temp_amp, temp_noise,
--   hum_base,  hum_amp,  hum_noise,
--   smoke_base, smoke_noise,
--   motion_prob,
--   s1_name, s1_base, s1_noise, s1_unit,
--   s2_name, s2_base, s2_noise, s2_unit,
--   s3_name, s3_base, s3_noise, s3_unit
--
-- NULL columns → sensor not present for that device.

WITH device_params (
  device_id,
  temp_base, temp_amp, temp_noise,
  hum_base,  hum_amp,  hum_noise,
  smoke_base, smoke_noise,
  motion_prob,
  s1_name, s1_base, s1_noise, s1_unit,
  s2_name, s2_base, s2_noise, s2_unit,
  s3_name, s3_base, s3_noise, s3_unit
) AS (VALUES
  -- Manufacturing ───────────────────────────────────────────────────────────
  -- mfg-assembly-line-01
  ('d1000001-0000-0000-0000-000000000000'::uuid, 72.0,8.0,5.0, 55.0,4.0,6.0, 18.0,10.0, 0.0, 'VIBRATION_G',1.2,0.6,'g', 'CO_PPM',8.0,3.0,'ppm', NULL,NULL,NULL,NULL),
  -- mfg-cnc-machine-01
  ('d1000002-0000-0000-0000-000000000000'::uuid, 65.0,6.0,4.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'VIBRATION_G',2.5,1.2,'g', 'SOUND_DB',82.0,8.0,'dB', 'CURRENT_A',35.0,5.0,'A'),
  -- mfg-air-quality-01
  ('d1000003-0000-0000-0000-000000000000'::uuid, 26.0,2.0,2.0, 58.0,3.0,5.0, NULL,NULL, 0.0, 'CO2_PPM',650.0,80.0,'ppm', 'CO_PPM',10.0,4.0,'ppm', 'PM25',18.0,7.0,'µg/m³'),
  -- mfg-motor-drive-01
  ('d1000004-0000-0000-0000-000000000000'::uuid, 62.0,7.0,4.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'CURRENT_A',72.0,8.0,'A', 'VOLTAGE_V',380.0,10.0,'V', 'POWER_W',35000.0,3000.0,'W'),
  -- mfg-compressor-01
  ('d1000005-0000-0000-0000-000000000000'::uuid, 55.0,10.0,5.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'PRESSURE',750.0,80.0,'kPa', 'VIBRATION_G',1.5,0.6,'g', 'CURRENT_A',18.0,3.0,'A'),
  -- mfg-welding-01
  ('d1000006-0000-0000-0000-000000000000'::uuid, 28.0,2.0,2.0, 52.0,3.0,4.0, 180.0,60.0, 0.0, 'CO_PPM',30.0,12.0,'ppm', 'VOC_INDEX',180.0,50.0,'idx', NULL,NULL,NULL,NULL),
  -- mfg-conveyor-01
  ('d1000007-0000-0000-0000-000000000000'::uuid, 38.0,5.0,3.0, NULL,NULL,NULL, NULL,NULL, 0.7, 'VIBRATION_G',0.8,0.4,'g', NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- mfg-utility-room-01
  ('d1000008-0000-0000-0000-000000000000'::uuid, 24.0,3.0,2.0, 58.0,4.0,5.0, NULL,NULL, 0.15, 'CO2_PPM',580.0,60.0,'ppm', NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),

  -- Cold Chain ─────────────────────────────────────────────────────────────
  -- cold-blast-freezer-01
  ('d2000001-0000-0000-0000-000000000000'::uuid, -18.0,2.0,1.5, 88.0,3.0,4.0, NULL,NULL, 0.0, NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- cold-walkin-fridge-01
  ('d2000002-0000-0000-0000-000000000000'::uuid, 3.0,1.0,0.8, 90.0,2.0,3.0, NULL,NULL, 0.15, NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- cold-display-case-01
  ('d2000003-0000-0000-0000-000000000000'::uuid, 4.0,1.5,0.8, 82.0,2.0,3.0, NULL,NULL, 0.0, NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- cold-pasteuriser-01
  ('d2000004-0000-0000-0000-000000000000'::uuid, 73.0,1.0,0.8, 55.0,3.0,4.0, NULL,NULL, 0.0, 'PRESSURE',180.0,20.0,'kPa', NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- cold-transport-01
  ('d2000005-0000-0000-0000-000000000000'::uuid, 1.0,1.5,1.0, 72.0,3.0,4.0, NULL,NULL, 0.0, 'VIBRATION_G',2.0,1.5,'g', 'SIGNAL_RSSI',-65.0,10.0,'dBm', NULL,NULL,NULL,NULL),
  -- cold-wine-cellar-01
  ('d2000006-0000-0000-0000-000000000000'::uuid, 13.0,0.5,0.3, 65.0,1.0,2.0, NULL,NULL, 0.05, 'LIGHT_LUX',12.0,8.0,'lux', NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),

  -- Data Center ────────────────────────────────────────────────────────────
  -- dc-server-rack-01
  ('d3000001-0000-0000-0000-000000000000'::uuid, 32.0,4.0,2.0, 42.0,2.0,3.0, NULL,NULL, 0.0, NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- dc-ups-01
  ('d3000002-0000-0000-0000-000000000000'::uuid, 28.0,2.0,1.5, NULL,NULL,NULL, NULL,NULL, 0.0, 'BATTERY_PCT',85.0,5.0,'%', 'BATTERY_V',48.5,0.5,'V', 'CURRENT_A',45.0,8.0,'A'),
  -- dc-pdu-01  (no temp/hum scalar columns)
  ('d3000003-0000-0000-0000-000000000000'::uuid, NULL,NULL,NULL, NULL,NULL,NULL, NULL,NULL, 0.0, 'POWER_W',18500.0,1500.0,'W', 'CURRENT_A',82.0,8.0,'A', 'VOLTAGE_V',228.0,3.0,'V'),
  -- dc-leak-sensor-01
  ('d3000004-0000-0000-0000-000000000000'::uuid, 22.0,1.0,0.5, NULL,NULL,NULL, NULL,NULL, 0.0, 'WATER_LEVEL_PCT',0.0,0.05,'%', NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- dc-crac-unit-01
  ('d3000005-0000-0000-0000-000000000000'::uuid, 20.0,3.0,1.5, 45.0,3.0,3.0, NULL,NULL, 0.0, 'PRESSURE',1013.0,2.0,'hPa', NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- dc-generator-01  (standby mode — low current)
  ('d3000006-0000-0000-0000-000000000000'::uuid, 35.0,5.0,3.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'VIBRATION_G',0.3,0.1,'g', 'CURRENT_A',8.0,3.0,'A', 'VOLTAGE_V',230.0,3.0,'V'),

  -- Agriculture ────────────────────────────────────────────────────────────
  -- agri-greenhouse-01
  ('d4000001-0000-0000-0000-000000000000'::uuid, 24.0,5.0,2.0, 72.0,8.0,5.0, NULL,NULL, 0.0, 'CO2_PPM',850.0,120.0,'ppm', 'LIGHT_LUX',45000.0,15000.0,'lux', NULL,NULL,NULL,NULL),
  -- agri-soil-01
  ('d4000002-0000-0000-0000-000000000000'::uuid, 18.0,3.0,1.5, 48.0,5.0,6.0, NULL,NULL, 0.0, 'PH',6.8,0.2,'pH', NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- agri-irrigation-01  (no temp/hum)
  ('d4000003-0000-0000-0000-000000000000'::uuid, NULL,NULL,NULL, NULL,NULL,NULL, NULL,NULL, 0.0, 'FLOW_LPM',320.0,80.0,'L/min', 'PRESSURE',280.0,40.0,'kPa', 'WATER_LEVEL_PCT',72.0,8.0,'%'),
  -- agri-weather-01
  ('d4000004-0000-0000-0000-000000000000'::uuid, 28.0,8.0,3.0, 65.0,12.0,8.0, NULL,NULL, 0.0, 'PRESSURE',1013.0,5.0,'hPa', 'UV_INDEX',6.0,2.0,'idx', 'LIGHT_LUX',60000.0,25000.0,'lux'),
  -- agri-silo-01
  ('d4000005-0000-0000-0000-000000000000'::uuid, 22.0,2.0,1.5, 42.0,3.0,4.0, NULL,NULL, 0.0, 'CO2_PPM',620.0,80.0,'ppm', NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- agri-livestock-01
  ('d4000006-0000-0000-0000-000000000000'::uuid, 26.0,3.0,2.0, 62.0,5.0,6.0, NULL,NULL, 0.0, 'CO2_PPM',1200.0,200.0,'ppm', 'VOC_INDEX',150.0,50.0,'idx', NULL,NULL,NULL,NULL),

  -- Healthcare ─────────────────────────────────────────────────────────────
  -- health-vaccine-fridge-01
  ('d5000001-0000-0000-0000-000000000000'::uuid, 4.5,0.5,0.3, 52.0,2.0,2.0, NULL,NULL, 0.0, NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- health-clean-room-01
  ('d5000002-0000-0000-0000-000000000000'::uuid, 20.0,0.5,0.3, 42.0,1.0,1.0, NULL,NULL, 0.0, 'PRESSURE',1016.0,1.0,'hPa', 'PM25',1.2,0.5,'µg/m³', 'PM10',2.5,0.8,'µg/m³'),
  -- health-autoclave-01
  ('d5000003-0000-0000-0000-000000000000'::uuid, 125.0,2.0,1.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'PRESSURE',230.0,15.0,'kPa', NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- health-lab-gas-01
  ('d5000004-0000-0000-0000-000000000000'::uuid, 22.0,1.0,0.8, 45.0,2.0,2.0, NULL,NULL, 0.0, 'CO_PPM',8.0,3.0,'ppm', 'O3_PPB',15.0,5.0,'ppb', 'VOC_INDEX',45.0,15.0,'idx'),
  -- health-operating-room-01
  ('d5000005-0000-0000-0000-000000000000'::uuid, 21.0,0.5,0.3, 50.0,1.0,1.0, NULL,NULL, 0.0, 'PRESSURE',1018.0,1.0,'hPa', 'PM25',0.8,0.3,'µg/m³', 'CO2_PPM',520.0,40.0,'ppm'),

  -- Energy ─────────────────────────────────────────────────────────────────
  -- energy-solar-inverter-01  (temp peaks at midday with sun)
  ('d6000001-0000-0000-0000-000000000000'::uuid, 42.0,15.0,5.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'VOLTAGE_V',680.0,80.0,'V', 'CURRENT_A',12.0,4.0,'A', 'POWER_W',6800.0,2500.0,'W'),
  -- energy-smart-meter-01  (no temp)
  ('d6000002-0000-0000-0000-000000000000'::uuid, NULL,NULL,NULL, NULL,NULL,NULL, NULL,NULL, 0.0, 'POWER_W',32000.0,8000.0,'W', 'CURRENT_A',68.0,18.0,'A', 'VOLTAGE_V',398.0,5.0,'V'),
  -- energy-transformer-01
  ('d6000003-0000-0000-0000-000000000000'::uuid, 58.0,12.0,4.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'CURRENT_A',280.0,60.0,'A', 'VIBRATION_G',0.8,0.3,'g', NULL,NULL,NULL,NULL),
  -- energy-water-plant-01
  ('d6000004-0000-0000-0000-000000000000'::uuid, 18.0,2.0,1.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'PH',7.2,0.15,'pH', 'FLOW_LPM',1850.0,150.0,'L/min', 'WATER_LEVEL_PCT',75.0,5.0,'%'),
  -- energy-wind-turbine-01
  ('d6000005-0000-0000-0000-000000000000'::uuid, 48.0,10.0,5.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'VIBRATION_G',2.8,1.2,'g', 'SOUND_DB',72.0,8.0,'dB', 'CURRENT_A',185.0,40.0,'A'),

  -- Smart Building ─────────────────────────────────────────────────────────
  -- building-hvac-01
  ('d7000001-0000-0000-0000-000000000000'::uuid, 22.0,4.0,1.5, 52.0,5.0,4.0, NULL,NULL, 0.0, 'CO2_PPM',680.0,120.0,'ppm', 'PRESSURE',1012.0,2.0,'hPa', NULL,NULL,NULL,NULL),
  -- building-fire-alarm-01
  ('d7000002-0000-0000-0000-000000000000'::uuid, 22.0,2.0,1.0, NULL,NULL,NULL, 8.0,5.0, 0.0, 'CO_PPM',3.0,2.0,'ppm', NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- building-occupancy-01  (no temp scalar)
  ('d7000003-0000-0000-0000-000000000000'::uuid, NULL,NULL,NULL, NULL,NULL,NULL, NULL,NULL, 0.55, 'CO2_PPM',720.0,150.0,'ppm', 'LIGHT_LUX',850.0,400.0,'lux', 'SOUND_DB',48.0,12.0,'dB'),
  -- building-elevator-01
  ('d7000004-0000-0000-0000-000000000000'::uuid, 28.0,3.0,2.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'VIBRATION_G',1.2,0.6,'g', 'CURRENT_A',28.0,8.0,'A', NULL,NULL,NULL,NULL),
  -- building-parking-01  (no temp scalar)
  ('d7000005-0000-0000-0000-000000000000'::uuid, NULL,NULL,NULL, NULL,NULL,NULL, NULL,NULL, 0.0, 'CO_PPM',12.0,6.0,'ppm', 'CO2_PPM',820.0,150.0,'ppm', 'PM25',22.0,10.0,'µg/m³'),
  -- building-water-tank-01
  ('d7000006-0000-0000-0000-000000000000'::uuid, 18.0,5.0,1.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'WATER_LEVEL_PCT',82.0,8.0,'%', 'PRESSURE',380.0,25.0,'kPa', NULL,NULL,NULL,NULL),

  -- Logistics ──────────────────────────────────────────────────────────────
  -- logistics-cold-truck-01
  ('d8000001-0000-0000-0000-000000000000'::uuid, 1.0,1.0,0.8, 68.0,4.0,5.0, NULL,NULL, 0.0, 'VIBRATION_G',3.5,2.0,'g', 'SIGNAL_RSSI',-72.0,15.0,'dBm', NULL,NULL,NULL,NULL),
  -- logistics-forklift-01
  ('d8000002-0000-0000-0000-000000000000'::uuid, 35.0,5.0,3.0, NULL,NULL,NULL, NULL,NULL, 0.0, 'BATTERY_PCT',72.0,8.0,'%', 'BATTERY_V',58.0,3.0,'V', 'VIBRATION_G',1.8,1.0,'g'),
  -- logistics-warehouse-air-01
  ('d8000003-0000-0000-0000-000000000000'::uuid, 24.0,3.0,2.0, 55.0,4.0,5.0, NULL,NULL, 0.0, 'CO2_PPM',750.0,120.0,'ppm', 'CO_PPM',15.0,5.0,'ppm', 'VOC_INDEX',88.0,30.0,'idx'),
  -- logistics-loading-dock-01
  ('d8000004-0000-0000-0000-000000000000'::uuid, 20.0,5.0,3.0, 60.0,8.0,8.0, NULL,NULL, 0.6, 'CO_PPM',10.0,5.0,'ppm', NULL,NULL,NULL,NULL, NULL,NULL,NULL,NULL),
  -- logistics-racking-01
  ('d8000005-0000-0000-0000-000000000000'::uuid, 22.0,2.0,1.5, NULL,NULL,NULL, NULL,NULL, 0.0, 'VIBRATION_G',0.5,0.2,'g', 'TILT_DEG',0.3,0.1,'°', NULL,NULL,NULL,NULL)
),
time_slots AS (
  SELECT ts
  FROM generate_series(
    NOW() - INTERVAL '47 hours',
    NOW() - INTERVAL '1 hour',
    INTERVAL '1 hour'
  ) AS ts
),
computed AS (
  SELECT
    p.device_id,
    t.ts,
    -- Temperature scalar column
    CASE WHEN p.temp_base IS NOT NULL THEN
      ROUND((p.temp_base
             + p.temp_amp * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-8)/24)
             + (RANDOM()-0.5)*p.temp_noise)::numeric, 1)
    END AS temperature,
    -- Humidity scalar column
    CASE WHEN p.hum_base IS NOT NULL THEN
      GREATEST(0, LEAST(100,
        ROUND((p.hum_base
               + p.hum_amp * SIN(2*PI()*(EXTRACT(HOUR FROM t.ts)-6)/24)
               + (RANDOM()-0.5)*p.hum_noise)::numeric, 1)
      ))
    END AS humidity,
    -- Smoke scalar column
    CASE WHEN p.smoke_base IS NOT NULL THEN
      GREATEST(0, ROUND((p.smoke_base + (RANDOM()-0.5)*p.smoke_noise)::numeric, 1))
    END AS smoke_ppm,
    -- Motion scalar column
    CASE WHEN p.motion_prob IS NOT NULL AND p.motion_prob > 0 THEN
      RANDOM() < p.motion_prob
    END AS motion,
    -- Extra sensor 1 computed value
    CASE WHEN p.s1_base IS NOT NULL THEN
      GREATEST(0, ROUND((p.s1_base + (RANDOM()-0.5)*p.s1_noise)::numeric, 2))
    END AS s1_val,
    p.s1_name, p.s1_unit,
    -- Extra sensor 2 computed value
    CASE WHEN p.s2_base IS NOT NULL THEN
      GREATEST(0, ROUND((p.s2_base + (RANDOM()-0.5)*p.s2_noise)::numeric, 2))
    END AS s2_val,
    p.s2_name, p.s2_unit,
    -- Extra sensor 3 computed value
    CASE WHEN p.s3_base IS NOT NULL THEN
      GREATEST(0, ROUND((p.s3_base + (RANDOM()-0.5)*p.s3_noise)::numeric, 2))
    END AS s3_val,
    p.s3_name, p.s3_unit
  FROM device_params p CROSS JOIN time_slots t
)
INSERT INTO telemetry
  (id, device_id, temperature, humidity, motion, smoke_ppm,
   timestamp, schema_version, readings)
SELECT
  gen_random_uuid(),
  device_id,
  temperature,
  humidity,
  motion,
  smoke_ppm,
  ts,
  2,
  jsonb_strip_nulls(jsonb_build_object(
    'TEMPERATURE', CASE WHEN temperature IS NOT NULL
      THEN jsonb_build_object('value', temperature, 'unit', '°C', 'quality', 'GOOD') END,
    'HUMIDITY',    CASE WHEN humidity IS NOT NULL
      THEN jsonb_build_object('value', humidity,    'unit', '%RH','quality', 'GOOD') END,
    'SMOKE_PPM',   CASE WHEN smoke_ppm IS NOT NULL
      THEN jsonb_build_object('value', smoke_ppm,   'unit', 'ppm','quality', 'GOOD') END,
    'MOTION',      CASE WHEN motion IS NOT NULL
      THEN jsonb_build_object('value', CASE WHEN motion THEN 1.0 ELSE 0.0 END, 'unit', 'bool', 'quality', 'GOOD') END,
    s1_name, CASE WHEN s1_val IS NOT NULL
      THEN jsonb_build_object('value', s1_val, 'unit', s1_unit, 'quality', 'GOOD') END,
    s2_name, CASE WHEN s2_val IS NOT NULL
      THEN jsonb_build_object('value', s2_val, 'unit', s2_unit, 'quality', 'GOOD') END,
    s3_name, CASE WHEN s3_val IS NOT NULL
      THEN jsonb_build_object('value', s3_val, 'unit', s3_unit, 'quality', 'GOOD') END
  ))
FROM computed;

-- ── 5. Hourly aggregates ──────────────────────────────────────────────────────

INSERT INTO telemetry_hourly_aggregates
  (id, device_id, hour_bucket,
   temp_avg, temp_min, temp_max,
   hum_avg,  hum_min,  hum_max,
   smoke_avg, smoke_max,
   motion_count, sample_count)
SELECT
  gen_random_uuid(),
  device_id,
  date_trunc('hour', timestamp),
  ROUND(AVG(temperature)::numeric, 2), MIN(temperature), MAX(temperature),
  ROUND(AVG(humidity)::numeric, 2),    MIN(humidity),    MAX(humidity),
  ROUND(AVG(smoke_ppm)::numeric, 2),   MAX(smoke_ppm),
  COUNT(*) FILTER (WHERE motion = true),
  COUNT(*)
FROM telemetry
WHERE device_id IN (
  'd1000001-0000-0000-0000-000000000000'::uuid, 'd1000002-0000-0000-0000-000000000000'::uuid,
  'd1000003-0000-0000-0000-000000000000'::uuid, 'd1000004-0000-0000-0000-000000000000'::uuid,
  'd1000005-0000-0000-0000-000000000000'::uuid, 'd1000006-0000-0000-0000-000000000000'::uuid,
  'd1000007-0000-0000-0000-000000000000'::uuid, 'd1000008-0000-0000-0000-000000000000'::uuid,
  'd2000001-0000-0000-0000-000000000000'::uuid, 'd2000002-0000-0000-0000-000000000000'::uuid,
  'd2000003-0000-0000-0000-000000000000'::uuid, 'd2000004-0000-0000-0000-000000000000'::uuid,
  'd2000005-0000-0000-0000-000000000000'::uuid, 'd2000006-0000-0000-0000-000000000000'::uuid,
  'd3000001-0000-0000-0000-000000000000'::uuid, 'd3000002-0000-0000-0000-000000000000'::uuid,
  'd3000003-0000-0000-0000-000000000000'::uuid, 'd3000004-0000-0000-0000-000000000000'::uuid,
  'd3000005-0000-0000-0000-000000000000'::uuid, 'd3000006-0000-0000-0000-000000000000'::uuid,
  'd4000001-0000-0000-0000-000000000000'::uuid, 'd4000002-0000-0000-0000-000000000000'::uuid,
  'd4000003-0000-0000-0000-000000000000'::uuid, 'd4000004-0000-0000-0000-000000000000'::uuid,
  'd4000005-0000-0000-0000-000000000000'::uuid, 'd4000006-0000-0000-0000-000000000000'::uuid,
  'd5000001-0000-0000-0000-000000000000'::uuid, 'd5000002-0000-0000-0000-000000000000'::uuid,
  'd5000003-0000-0000-0000-000000000000'::uuid, 'd5000004-0000-0000-0000-000000000000'::uuid,
  'd5000005-0000-0000-0000-000000000000'::uuid,
  'd6000001-0000-0000-0000-000000000000'::uuid, 'd6000002-0000-0000-0000-000000000000'::uuid,
  'd6000003-0000-0000-0000-000000000000'::uuid, 'd6000004-0000-0000-0000-000000000000'::uuid,
  'd6000005-0000-0000-0000-000000000000'::uuid,
  'd7000001-0000-0000-0000-000000000000'::uuid, 'd7000002-0000-0000-0000-000000000000'::uuid,
  'd7000003-0000-0000-0000-000000000000'::uuid, 'd7000004-0000-0000-0000-000000000000'::uuid,
  'd7000005-0000-0000-0000-000000000000'::uuid, 'd7000006-0000-0000-0000-000000000000'::uuid,
  'd8000001-0000-0000-0000-000000000000'::uuid, 'd8000002-0000-0000-0000-000000000000'::uuid,
  'd8000003-0000-0000-0000-000000000000'::uuid, 'd8000004-0000-0000-0000-000000000000'::uuid,
  'd8000005-0000-0000-0000-000000000000'::uuid
)
GROUP BY device_id, date_trunc('hour', timestamp)
ON CONFLICT ON CONSTRAINT uq_telemetry_hourly DO NOTHING;

-- ── 6. Sample alerts (~40 rows — representative per industry) ─────────────────

INSERT INTO alerts (id, device_id, level, message, created_at, acknowledged, organization_id)
VALUES
  -- Manufacturing
  (gen_random_uuid(), 'd1000001-0000-0000-0000-000000000000', 'WARNING',  'SMOKE_PPM exceeded threshold: 168.4 ppm (warn: 150)', NOW() - INTERVAL '6 hours',  false, 'b1000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd1000001-0000-0000-0000-000000000000', 'CRITICAL', 'SMOKE_PPM exceeded threshold: 278.1 ppm (crit: 250)', NOW() - INTERVAL '2 hours',  false, 'b1000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd1000002-0000-0000-0000-000000000000', 'WARNING',  'VIBRATION_G exceeded threshold: 3.72 g (warn: 3.5)', NOW() - INTERVAL '10 hours', true,  'b1000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd1000004-0000-0000-0000-000000000000', 'WARNING',  'TEMPERATURE exceeded threshold: 76.3 °C (warn: 75)', NOW() - INTERVAL '4 hours',  false, 'b1000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd1000005-0000-0000-0000-000000000000', 'CRITICAL', 'PRESSURE exceeded threshold: 1118 kPa (crit: 1100)',  NOW() - INTERVAL '1 hour',   false, 'b1000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd1000006-0000-0000-0000-000000000000', 'CRITICAL', 'SMOKE_PPM exceeded threshold: 412.6 ppm (crit: 400)', NOW() - INTERVAL '3 hours',  false, 'b1000000-0000-0000-0000-000000000000'),
  -- Cold Chain
  (gen_random_uuid(), 'd2000001-0000-0000-0000-000000000000', 'CRITICAL', 'TEMPERATURE exceeded threshold: -9.2 °C (crit: -10) — freezer warming', NOW() - INTERVAL '5 hours', false, 'b2000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd2000002-0000-0000-0000-000000000000', 'WARNING',  'TEMPERATURE exceeded threshold: 6.5 °C (warn: 6)', NOW() - INTERVAL '8 hours',  true,  'b2000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd2000004-0000-0000-0000-000000000000', 'CRITICAL', 'TEMPERATURE dropped below threshold: 71.8 °C (crit: 72) — pasteurisation failure risk', NOW() - INTERVAL '30 minutes', false, 'b2000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd2000005-0000-0000-0000-000000000000', 'WARNING',  'VIBRATION_G exceeded threshold: 4.87 g (warn: 4.5) — road shock event', NOW() - INTERVAL '2 hours', true, 'b2000000-0000-0000-0000-000000000000'),
  -- Data Center
  (gen_random_uuid(), 'd3000001-0000-0000-0000-000000000000', 'WARNING',  'TEMPERATURE exceeded threshold: 36.2 °C (warn: 35) — check CRAC airflow', NOW() - INTERVAL '3 hours',  false, 'b3000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd3000002-0000-0000-0000-000000000000', 'WARNING',  'BATTERY_PCT dropped below threshold: 22 % (warn: 25) — UPS discharging', NOW() - INTERVAL '45 minutes', false, 'b3000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd3000003-0000-0000-0000-000000000000', 'CRITICAL', 'POWER_W exceeded threshold: 29200 W (crit: 28500) — PDU overload', NOW() - INTERVAL '20 minutes', false, 'b3000000-0000-0000-0000-000000000000'),
  -- Agriculture
  (gen_random_uuid(), 'd4000001-0000-0000-0000-000000000000', 'WARNING',  'HUMIDITY exceeded threshold: 91.3 %RH (warn: 90) — botrytis risk', NOW() - INTERVAL '4 hours',  false, 'b4000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd4000002-0000-0000-0000-000000000000', 'WARNING',  'PH exceeded threshold: 7.62 pH (warn: 7.5) — check nutrient dosing', NOW() - INTERVAL '6 hours',  true,  'b4000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd4000003-0000-0000-0000-000000000000', 'CRITICAL', 'WATER_LEVEL_PCT dropped below threshold: 6.8 % (crit: 8) — reservoir low', NOW() - INTERVAL '1 hour', false, 'b4000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd4000005-0000-0000-0000-000000000000', 'WARNING',  'TEMPERATURE exceeded threshold: 29.4 °C (warn: 28) — hotspot risk in silo', NOW() - INTERVAL '2 hours', false, 'b4000000-0000-0000-0000-000000000000'),
  -- Healthcare
  (gen_random_uuid(), 'd5000001-0000-0000-0000-000000000000', 'CRITICAL', 'TEMPERATURE exceeded threshold: 8.3 °C (crit: 8.0) — vaccine cold chain breach', NOW() - INTERVAL '15 minutes', false, 'b5000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd5000002-0000-0000-0000-000000000000', 'WARNING',  'PM25 exceeded threshold: 5.8 µg/m³ (warn: 5) — clean room particulate event', NOW() - INTERVAL '3 hours', true, 'b5000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd5000004-0000-0000-0000-000000000000', 'WARNING',  'CO_PPM exceeded threshold: 26.2 ppm (warn: 25) — fume extraction check', NOW() - INTERVAL '7 hours', true, 'b5000000-0000-0000-0000-000000000000'),
  -- Energy
  (gen_random_uuid(), 'd6000001-0000-0000-0000-000000000000', 'WARNING',  'TEMPERATURE exceeded threshold: 71.8 °C (warn: 70) — inverter cooling check', NOW() - INTERVAL '5 hours', true,  'b6000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd6000003-0000-0000-0000-000000000000', 'WARNING',  'TEMPERATURE exceeded threshold: 81.4 °C (warn: 80) — transformer oil check', NOW() - INTERVAL '3 hours', false, 'b6000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd6000004-0000-0000-0000-000000000000', 'WARNING',  'PH exceeded threshold: 8.62 pH (warn: 8.5) — dosing adjustment needed', NOW() - INTERVAL '2 hours', false, 'b6000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd6000005-0000-0000-0000-000000000000', 'CRITICAL', 'VIBRATION_G exceeded threshold: 8.3 g (crit: 8.0) — drivetrain inspection required', NOW() - INTERVAL '1 hour',  false, 'b6000000-0000-0000-0000-000000000000'),
  -- Smart Building
  (gen_random_uuid(), 'd7000001-0000-0000-0000-000000000000', 'WARNING',  'CO2_PPM exceeded threshold: 1082 ppm (warn: 1000) — increase fresh air intake', NOW() - INTERVAL '4 hours',  true,  'b7000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd7000002-0000-0000-0000-000000000000', 'CRITICAL', 'SMOKE_PPM exceeded threshold: 648 ppm (crit: 600) — fire alarm triggered', NOW() - INTERVAL '30 minutes', false, 'b7000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd7000005-0000-0000-0000-000000000000', 'WARNING',  'CO_PPM exceeded threshold: 26.5 ppm (warn: 25) — ventilate parking level P2', NOW() - INTERVAL '2 hours',  false, 'b7000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd7000006-0000-0000-0000-000000000000', 'CRITICAL', 'WATER_LEVEL_PCT dropped below threshold: 8.4 % (crit: 10) — tank refill required', NOW() - INTERVAL '1 hour',  false, 'b7000000-0000-0000-0000-000000000000'),
  -- Logistics
  (gen_random_uuid(), 'd8000001-0000-0000-0000-000000000000', 'CRITICAL', 'TEMPERATURE exceeded threshold: 6.8 °C (crit: 6.0) — cold chain SLA breach', NOW() - INTERVAL '2 hours',  false, 'b8000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd8000002-0000-0000-0000-000000000000', 'WARNING',  'BATTERY_PCT dropped below threshold: 23 % (warn: 25) — forklift recharge needed', NOW() - INTERVAL '3 hours',  true,  'b8000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd8000003-0000-0000-0000-000000000000', 'WARNING',  'CO_PPM exceeded threshold: 21.3 ppm (warn: 20) — forklift exhaust — check ventilation', NOW() - INTERVAL '1 hour', false, 'b8000000-0000-0000-0000-000000000000'),
  (gen_random_uuid(), 'd8000005-0000-0000-0000-000000000000', 'CRITICAL', 'TILT_DEG exceeded threshold: 5.4 ° (crit: 5) — racking inspection required immediately', NOW() - INTERVAL '30 minutes', false, 'b8000000-0000-0000-0000-000000000000');

COMMIT;

\echo ''
\echo '=== Industry Seed Complete ==='
\echo '  Organizations : 8  (manufacturing, cold-chain, datacenter, agriculture,'
\echo '                       healthcare, energy, smart-building, logistics)'
\echo '  Admin users   : 8  (org-{name}-admin / sentinel123)'
\echo '  Devices       : 47  (across 8 industries)'
\echo '  Telemetry     : 47 × 48 h hourly = ~2 256 rows'
\echo '  Alerts        : ~32 (representative per industry)'
\echo ''
\echo '  Login per org → POST /api/v1/auth/login'
\echo '    { "username": "org-manufacturing-admin", "password": "sentinel123" }'
\echo ''
