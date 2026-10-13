'use strict'

const mqtt = require('mqtt')

const BROKER_URL = process.env.MQTT_BROKER || 'mqtt://localhost:1883'
const ORG_ID = process.env.ORG_ID || 'aaaaaaaa-0000-0000-0000-000000000001'
const TARGET_DEVICE = process.env.TARGET_DEVICE || 'sensor-1'
const TOPIC = process.env.MQTT_TOPIC || `sentinel/tenants/${ORG_ID}/devices/${TARGET_DEVICE}/telemetry`

console.log('====================================================')
console.log('🔥 Thermal Runaway Crisis Live Injection Drill')
console.log('====================================================')
console.log(`Broker URL    : ${BROKER_URL}`)
console.log(`Target Device : ${TARGET_DEVICE}`)
console.log(`Target Topic  : ${TOPIC}`)
console.log('====================================================')

const client = mqtt.connect(BROKER_URL, {
  clientId: `thermal-drill-${Date.now()}`,
  reconnectPeriod: 2000,
  connectTimeout: 5000,
})

client.on('connect', () => {
  console.log('[Drill] Connected to MQTT broker.')
  runDrillSequence()
})

client.on('error', (err) => {
  console.error('[Drill] MQTT Error:', err.message)
  process.exit(1)
})

function sendPayload(stageName, temp, hum, smoke, callback) {
  const now = Date.now()
  const payload = {
    schemaVersion: 2,
    deviceId: TARGET_DEVICE,
    organizationId: ORG_ID,
    timestamp: new Date(now).toISOString(),
    edgeTimestamp: new Date(now - 10).toISOString(),
    quality: 'GOOD',
    readings: {
      TEMPERATURE: { value: temp, unit: '°C', quality: 'GOOD' },
      HUMIDITY: { value: hum, unit: '%', quality: 'GOOD' },
      SMOKE_PPM: { value: smoke, unit: 'ppm', quality: 'GOOD' },
      MOTION: { value: 0.0, unit: 'state', quality: 'GOOD' }
    },
    metadata: {
      drillStage: stageName,
      injectedBy: 'thermal-runaway-injection.js'
    }
  }

  const msg = JSON.stringify(payload)
  client.publish(TOPIC, msg, { qos: 1 }, (err) => {
    if (err) {
      console.error(`[${stageName}] Failed to publish:`, err.message)
    } else {
      console.log(`[${stageName}] Injected -> Temp: ${temp}°C | Humidity: ${hum}% | Smoke: ${smoke} ppm`)
    }
    if (callback) callback()
  })
}

function runDrillSequence() {
  // Stage 1: Nominal Baseline (25°C, 50%, 1.0 ppm)
  console.log('\n--- Stage 1: Nominal Steady-State Calibration ---')
  sendPayload('STAGE_1_NOMINAL', 25.0, 50.0, 1.0, () => {
    setTimeout(() => {
      // Stage 2: Elevated Thermal Warning (48°C, 35%, 15.0 ppm)
      console.log('\n--- Stage 2: Incipient Heating Drift ---')
      sendPayload('STAGE_2_HEATING', 48.0, 35.0, 15.0, () => {
        setTimeout(() => {
          // Stage 3: Severe Thermal Runaway Crisis (89.5°C, 11.2%, 120.0 ppm)
          console.log('\n--- Stage 3: Full Thermal Runaway Crisis Surge ---')
          sendPayload('STAGE_3_THERMAL_RUNAWAY', 89.5, 11.2, 120.0, () => {
            setTimeout(() => {
              // Stage 4: Consecutive Crisis Injections to verify Alert Deduplication
              console.log('\n--- Stage 4: Rapid Repeat Burst (Asserting Distributed Deduplication) ---')
              sendPayload('STAGE_4_REPEAT_1', 91.0, 10.5, 135.0, () => {
                sendPayload('STAGE_4_REPEAT_2', 92.5, 9.8, 140.0, () => {
                  console.log('\n✓ Thermal runaway drill sequence published successfully.')
                  console.log('✓ Inspect Platform Alerts UI or run verify script to assert detection.')
                  setTimeout(() => {
                    client.end()
                    process.exit(0)
                  }, 1000)
                })
              })
            }, 1500)
          })
        }, 1500)
      })
    }, 1500)
  })
}
