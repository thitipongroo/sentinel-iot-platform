/**
 * Sentinel IoT Platform API — v1.0.0
 *
 * AUTO-GENERATED from the OpenAPI spec at /api-docs.
 * DO NOT edit manually — run `npm run generate:types` instead.
 *
 * Source: POST /api/v1/auth/login → GET /api-docs → openapi-typescript
 */

// ── Auth ────────────────────────────────────────────────────────────────────

export interface AuthRequest {
  /** @minLength 3 */
  username: string;
  /** @minLength 8 */
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  role: "ADMIN" | "OPERATOR";
  username: string;
}

// ── Devices ─────────────────────────────────────────────────────────────────

export type LifecycleStatus = "PROVISIONED" | "ACTIVE" | "INACTIVE" | "DECOMMISSIONED";
export type ConnectionStatus = "ONLINE" | "OFFLINE";

export interface DeviceRequest {
  /** @minLength 2 @maxLength 100 */
  name: string;
  location?: string;
  firmwareVersion?: string;
}

export interface DeviceLifecycleRequest {
  lifecycleStatus: LifecycleStatus;
}

export interface FirmwareUpdateRequest {
  /** @minLength 1 */
  firmwareVersion: string;
}

export interface DeviceCapabilityRequest {
  capabilities: Record<string, SensorCapability>;
}

export interface Device {
  /** UUID */
  id: string;
  name: string;
  status: ConnectionStatus;
  lifecycleStatus: LifecycleStatus;
  location?: string;
  firmwareVersion?: string;
  lastSeen?: string; // ISO-8601 date-time
  capabilities?: Record<string, SensorCapability>;
  organizationId?: string;
}

// ── Telemetry ────────────────────────────────────────────────────────────────

export type ReadingQuality = "GOOD" | "UNCERTAIN" | "BAD";
export type SensorTypeName =
  | "TEMPERATURE" | "HUMIDITY" | "SMOKE_PPM" | "CO2_PPM"
  | "MOTION" | "PRESSURE_HPA" | "PRESSURE_PSI" | "BATTERY_PCT"
  | "BATTERY_VOLTAGE" | "SIGNAL_RSSI" | "SIGNAL_SNR" | "LIGHT_LUX"
  | "UV_INDEX" | "NOISE_DB" | "VIBRATION_G" | "CURRENT_AMP"
  | "VOLTAGE_V" | "POWER_W" | "ENERGY_KWH" | "FLOW_LPM"
  | "LEVEL_PCT" | "DISTANCE_CM" | "DOOR_OPEN" | "WATER_LEAK"
  | "FLAME_DETECTED" | "GAS_PPM" | "PH_LEVEL" | "CONDUCTIVITY"
  | "SOIL_MOISTURE" | "WIND_SPEED" | "WIND_DIRECTION" | "RAIN_MM";

export interface SensorReading {
  value: number | null;
  unit: string;
  quality: ReadingQuality;
}

export interface EdgeMetadata {
  firmwareVersion?: string;
  ipAddress?: string;
  uptimeSeconds?: number;
  rssi?: number;
  snr?: number;
  batteryVoltage?: number;
  batteryPct?: number;
  freeHeapBytes?: number;
  protocol?: string;
}

export interface Telemetry {
  /** UUID */
  id: string;
  /** UUID */
  deviceId: string;
  schemaVersion: number;
  timestamp: string; // ISO-8601 date-time
  // v1 scalar fields (populated for schemaVersion == 1)
  temperature?: number;
  humidity?: number;
  smokePpm?: number;
  motion?: boolean;
  // v2 dynamic readings (populated for schemaVersion >= 2)
  readings?: Record<SensorTypeName, SensorReading>;
  // edge metadata
  edgeFirmwareVersion?: string;
  edgeIp?: string;
  edgeUptimeSeconds?: number;
  edgeRssi?: number;
  edgeSnr?: number;
  edgeBatteryVoltage?: number;
  edgeBatteryPct?: number;
  edgeFreeHeapBytes?: number;
  edgeProtocol?: string;
}

export interface TelemetryHourlyAggregate {
  deviceId: string;
  hour: string; // ISO-8601 date-time, truncated to hour
  avgTemperature?: number;
  maxTemperature?: number;
  minTemperature?: number;
  avgHumidity?: number;
  avgSmokePpm?: number;
  motionEvents?: number;
  sampleCount: number;
}

export interface TelemetryStats {
  lastMinute: number;
  replayQueueSize: number;
}

// ── Sensor Capabilities ───────────────────────────────────────────────────────

export type ThresholdDirection = "ABOVE" | "BELOW";

export interface SensorCapability {
  unit: string;
  minOperational?: number;
  maxOperational?: number;
  warnThreshold?: number;
  critThreshold?: number;
  thresholdDirection: ThresholdDirection;
  enabled: boolean;
  decimalPlaces: number;
}

// ── Alerts ───────────────────────────────────────────────────────────────────

export type AlertSeverity = "WARNING" | "CRITICAL";

export interface Alert {
  /** UUID */
  id: string;
  /** UUID */
  deviceId: string;
  deviceName: string;
  severity: AlertSeverity;
  message: string;
  acknowledged: boolean;
  acknowledgedBy?: string;
  acknowledgedAt?: string; // ISO-8601
  createdAt: string; // ISO-8601
  /** SensorType name if from capability-aware alert engine, e.g. "TEMPERATURE" */
  sensorType?: string;
  triggeringValue?: number;
  threshold?: number;
}

// ── API Error ────────────────────────────────────────────────────────────────

export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
}

// ── Utility helpers (not generated — hand-written) ────────────────────────────

/** Narrows an unknown response to a typed API error. */
export function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === "object" &&
    value !== null &&
    "status" in value &&
    "message" in value
  );
}
