'use strict'

const net = require('net')

const BROKER_URL = process.env.MQTT_BROKER || 'mqtt://localhost:1883'
const ORG_ID = process.env.ORG_ID || 'aaaaaaaa-0000-0000-0000-000000000001'
const TARGET_DEVICE = process.env.TARGET_DEVICE || 'sensor-1'
const TOPIC = process.env.MQTT_TOPIC || `tenants/${ORG_ID}/devices/${TARGET_DEVICE}/telemetry`

console.log('====================================================')
console.log('🔥 Thermal Runaway Crisis Live Injection Drill')
console.log('====================================================')
console.log(`Broker URL    : ${BROKER_URL}`)
console.log(`Target Device : ${TARGET_DEVICE}`)
console.log(`Target Topic  : ${TOPIC}`)
console.log('====================================================')

// Try to use 'mqtt' package if available, otherwise fallback to zero-dependency raw MQTT over net.Socket
let mqtt
try {
  mqtt = require('mqtt')
} catch (_) {
  mqtt = null
}

function createClient(brokerUrl, onConnect, onError) {
  if (mqtt) {
    const client = mqtt.connect(brokerUrl, {
      clientId: `thermal-drill-${Date.now()}`,
      reconnectPeriod: 2000,
      connectTimeout: 5000,
    })
    client.on('connect', () => {
      onConnect({
        publish: (topic, msg, cb) => client.publish(topic, msg, { qos: 1 }, cb),
        end: () => client.end(),
      })
    })
    client.on('error', onError)
    return
  }

  // Fallback: Zero-dependency raw MQTT 3.1.1 socket client
  const url = new URL(brokerUrl.startsWith('mqtt://') ? brokerUrl : `mqtt://${brokerUrl}`)
  const host = url.hostname || 'localhost'
  const port = parseInt(url.port || '1883', 10)
  const clientId = `thermal-drill-${Date.now()}`

  function encodeRemainingLength(length) {
    const buffer = []
    do {
      let encodedByte = length % 128
      length = Math.floor(length / 128)
      if (length > 0) encodedByte = encodedByte | 128
      buffer.push(encodedByte)
    } while (length > 0)
    return Buffer.from(buffer)
  }

  function makeConnectPacket(cid) {
    const protocolName = Buffer.from('MQTT')
    const protocolLevel = 4 // MQTT 3.1.1
    const connectFlags = 0x02 // Clean session
    const keepAlive = 60
    const cidBuf = Buffer.from(cid, 'utf8')

    const variableHeader = Buffer.alloc(10)
    variableHeader.writeUInt16BE(4, 0)
    protocolName.copy(variableHeader, 2)
    variableHeader.writeUInt8(protocolLevel, 6)
    variableHeader.writeUInt8(connectFlags, 7)
    variableHeader.writeUInt16BE(keepAlive, 8)

    const payload = Buffer.alloc(2 + cidBuf.length)
    payload.writeUInt16BE(cidBuf.length, 0)
    cidBuf.copy(payload, 2)

    const remLen = encodeRemainingLength(variableHeader.length + payload.length)
    return Buffer.concat([Buffer.from([0x10]), remLen, variableHeader, payload])
  }

  function makePublishPacket(topic, message) {
    const topicBuf = Buffer.from(topic, 'utf8')
    const messageBuf = Buffer.from(message, 'utf8')
    const topicLen = Buffer.alloc(2)
    topicLen.writeUInt16BE(topicBuf.length, 0)

    const remLen = encodeRemainingLength(2 + topicBuf.length + messageBuf.length)
    return Buffer.concat([Buffer.from([0x30]), remLen, topicLen, topicBuf, messageBuf])
  }

  const socket = net.createConnection({ host, port }, () => {
    socket.write(makeConnectPacket(clientId))
  })

  let connected = false
  socket.on('data', () => {
    if (!connected) {
      connected = true
      onConnect({
        publish: (topic, msg, cb) => {
          socket.write(makePublishPacket(topic, msg), cb)
        },
        end: () => {
          socket.write(Buffer.from([0xe0, 0x00]), () => socket.end())
        },
      })
    }
  })

  socket.on('error', onError)
}

createClient(
  BROKER_URL,
  (client) => {
    console.log('[Drill] Connected to MQTT broker.')
    runDrillSequence(client)
  },
  (err) => {
    console.error('[Drill] MQTT Error:', err.message)
    process.exit(1)
  }
)

function sendPayload(client, stageName, temp, hum, smoke, callback) {
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
      MOTION: { value: 0.0, unit: 'state', quality: 'GOOD' },
    },
    metadata: {
      drillStage: stageName,
      injectedBy: 'thermal-runaway-injection.js',
    },
  }

  const msg = JSON.stringify(payload)
  client.publish(TOPIC, msg, (err) => {
    if (err) {
      console.error(`[${stageName}] Failed to publish:`, err.message)
    } else {
      console.log(`[${stageName}] Injected -> Temp: ${temp}°C | Humidity: ${hum}% | Smoke: ${smoke} ppm`)
    }
    if (callback) callback()
  })
}

function runDrillSequence(client) {
  // Stage 1: Nominal Baseline (25°C, 50%, 1.0 ppm)
  console.log('\n--- Stage 1: Nominal Steady-State Calibration ---')
  sendPayload(client, 'STAGE_1_NOMINAL', 25.0, 50.0, 1.0, () => {
    setTimeout(() => {
      // Stage 2: Elevated Thermal Warning (48°C, 35%, 15.0 ppm)
      console.log('\n--- Stage 2: Incipient Heating Drift ---')
      sendPayload(client, 'STAGE_2_HEATING', 48.0, 35.0, 15.0, () => {
        setTimeout(() => {
          // Stage 3: Severe Thermal Runaway Crisis (89.5°C, 11.2%, 120.0 ppm)
          console.log('\n--- Stage 3: Full Thermal Runaway Crisis Surge ---')
          sendPayload(client, 'STAGE_3_THERMAL_RUNAWAY', 89.5, 11.2, 120.0, () => {
            setTimeout(() => {
              // Stage 4: Consecutive Crisis Injections to verify Alert Deduplication
              console.log('\n--- Stage 4: Rapid Repeat Burst (Asserting Distributed Deduplication) ---')
              sendPayload(client, 'STAGE_4_REPEAT_1', 91.0, 10.5, 135.0, () => {
                sendPayload(client, 'STAGE_4_REPEAT_2', 92.5, 9.8, 140.0, () => {
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

