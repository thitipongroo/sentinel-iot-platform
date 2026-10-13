'use strict'

const mqtt = require('mqtt')

const BROKER_URL = process.env.MQTT_BROKER || 'mqtt://localhost:1883'
const TOPIC_PREFIX = process.env.MQTT_TOPIC_PREFIX || 'sentinel/tenants'
const LEGACY_TOPIC = process.env.MQTT_TOPIC || 'factory/telemetry'
const USE_LEGACY_TOPIC = process.env.USE_LEGACY_TOPIC === 'true'
const ORG_ID = process.env.ORG_ID || 'aaaaaaaa-0000-0000-0000-000000000001'
const SCHEMA_VERSION = parseInt(process.env.SCHEMA_VERSION || '2') // 1 = v1 legacy, 2 = v2 capability-aware

// Throughput & Scale Configuration
const TARGET_RPS = parseInt(process.env.TARGET_RPS || '100') // messages per second across all devices
const INTERVAL_MS = parseInt(process.env.INTERVAL_MS || '1000')
const CLIENT_POOL_SIZE = parseInt(process.env.CLIENT_POOL_SIZE || '5')
const DEVICE_COUNT = parseInt(process.env.DEVICE_COUNT || '50')
const DEVICE_PREFIX = process.env.DEVICE_PREFIX || 'sensor'

// Anomaly Mode: 'NOMINAL', 'THERMAL_RUNAWAY', 'SMOKE_SPIKE', 'RANDOM_SPIKES'
const ANOMALY_MODE = process.env.ANOMALY_MODE || 'RANDOM_SPIKES'

console.log('====================================================')
console.log('⚡ Sentinel IoT High-Throughput & Anomaly Simulator')
console.log('====================================================')
console.log(`Broker URL         : ${BROKER_URL}`)
console.log(`Schema Version     : v${SCHEMA_VERSION}`)
console.log(`Organization ID    : ${ORG_ID}`)
console.log(`Device Count       : ${DEVICE_COUNT} devices`)
console.log(`Client Pool Size   : ${CLIENT_POOL_SIZE} connections`)
console.log(`Target Throughput  : ${TARGET_RPS} msgs/sec`)
console.log(`Anomaly Mode       : ${ANOMALY_MODE}`)
console.log('====================================================')

const devices = Array.from({ length: DEVICE_COUNT }, (_, i) => `${DEVICE_PREFIX}-${i + 1}`)

function randomInRange(min, max) {
  return parseFloat((Math.random() * (max - min) + min).toFixed(2))
}

function generatePayload(deviceId, anomalyMode = ANOMALY_MODE) {
  const now = Date.now()
  let temp, hum, smoke, motion

  if (anomalyMode === 'THERMAL_RUNAWAY') {
    // Thermal Runaway: High Temp (85-98°C), Collapsing Humidity (10-20%), Surging Smoke (75-250 ppm)
    temp = randomInRange(85.0, 96.0)
    hum = randomInRange(10.0, 18.0)
    smoke = randomInRange(75.0, 220.0)
    motion = false
  } else if (anomalyMode === 'NOMINAL') {
    // Nominal steady-state operation within tight μ ± 1.5σ envelope
    temp = randomInRange(23.5, 26.5)
    hum = randomInRange(48.0, 54.0)
    smoke = randomInRange(0.5, 2.5)
    motion = Math.random() < 0.10
  } else if (anomalyMode === 'SMOKE_SPIKE') {
    temp = randomInRange(25.0, 30.0)
    hum = randomInRange(45.0, 55.0)
    smoke = randomInRange(180.0, 300.0)
    motion = false
  } else {
    // RANDOM_SPIKES (Default industrial jitter with occasional anomalies)
    const isTempSpike = Math.random() < 0.04
    const isSmokeSpike = Math.random() < 0.03
    const isThermalRunaway = Math.random() < 0.01

    if (isThermalRunaway) {
      temp = randomInRange(86.0, 95.0)
      hum = randomInRange(12.0, 19.0)
      smoke = randomInRange(80.0, 190.0)
      motion = false
    } else {
      temp = isTempSpike ? randomInRange(81.0, 92.0) : randomInRange(22.0, 32.0)
      hum = randomInRange(40.0, 65.0)
      smoke = isSmokeSpike ? randomInRange(150.0, 280.0) : randomInRange(0.5, 4.0)
      motion = Math.random() < 0.15
    }
  }

  if (SCHEMA_VERSION === 1) {
    // Legacy fixed-field payload
    return {
      deviceId,
      organizationId: ORG_ID,
      temperature: temp,
      humidity: hum,
      smokePpm: smoke,
      motion,
      timestamp: now
    }
  }

  // Schema Version 2: Capability-aware readings map + edge metadata
  return {
    schemaVersion: 2,
    deviceId,
    organizationId: ORG_ID,
    timestamp: new Date(now).toISOString(),
    edgeTimestamp: new Date(now - 15).toISOString(),
    quality: 'GOOD',
    readings: {
      TEMPERATURE: { value: temp, unit: '°C', quality: 'GOOD' },
      HUMIDITY: { value: hum, unit: '%', quality: 'GOOD' },
      SMOKE_PPM: { value: smoke, unit: 'ppm', quality: 'GOOD' },
      MOTION: { value: motion ? 1.0 : 0.0, unit: 'state', quality: 'GOOD' }
    },
    metadata: {
      gatewayId: 'gw-edge-alpha',
      firmware: '2.4.0',
      anomalySimulated: anomalyMode !== 'NOMINAL'
    }
  }
}

// ── Client Connection Pool ──────────────────────────────────────────────────
const clients = []
let connectedCount = 0
let publishedTotal = 0
let errorsTotal = 0
let startTime = Date.now()

for (let i = 0; i < CLIENT_POOL_SIZE; i++) {
  const clientId = `sentinel-sim-pool-${i}-${Date.now()}`
  const client = mqtt.connect(BROKER_URL, {
    clientId,
    reconnectPeriod: 2000,
    connectTimeout: 5000,
    clean: true
  })

  client.on('connect', () => {
    connectedCount++
    if (connectedCount === CLIENT_POOL_SIZE) {
      console.log(`[Simulator] All ${CLIENT_POOL_SIZE} MQTT pool connections established. Starting load generator…`)
      startLoadGenerator()
    }
  })

  client.on('error', (err) => {
    errorsTotal++
    console.error(`[Pool ${i}] Error:`, err.message)
  })

  clients.push(client)
}

function startLoadGenerator() {
  startTime = Date.now()
  const intervalMs = Math.max(10, Math.floor(1000 / TARGET_RPS))
  let deviceIndex = 0

  // Interval timer for sustained high-rate dispatch
  const timer = setInterval(() => {
    const deviceId = devices[deviceIndex % devices.length]
    deviceIndex++

    const client = clients[deviceIndex % clients.length]
    const payload = generatePayload(deviceId)
    const topic = USE_LEGACY_TOPIC
      ? LEGACY_TOPIC
      : `${TOPIC_PREFIX}/${ORG_ID}/devices/${deviceId}/telemetry`

    const message = JSON.stringify(payload)
    client.publish(topic, message, { qos: 1 }, (err) => {
      if (err) {
        errorsTotal++
      } else {
        publishedTotal++
      }
    })
  }, intervalMs)

  // Performance telemetry logging every 3 seconds
  setInterval(() => {
    const elapsedSec = (Date.now() - startTime) / 1000
    const actualRps = publishedTotal / elapsedSec
    console.log(
      `[Throughput] Published: ${publishedTotal} msgs | ` +
      `Rate: ${actualRps.toFixed(1)} msgs/sec | ` +
      `Target: ${TARGET_RPS} | ` +
      `Errors: ${errorsTotal} | ` +
      `Mode: ${ANOMALY_MODE}`
    )
  }, 3000)

  process.on('SIGINT', () => {
    clearInterval(timer)
    console.log('\n[Simulator] Stopping load generator…')
    clients.forEach((c) => c.end())
    process.exit(0)
  })
}
