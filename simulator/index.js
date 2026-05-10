'use strict'

const mqtt = require('mqtt')

const BROKER_URL = process.env.MQTT_BROKER || 'mqtt://localhost:1883'
const TOPIC = process.env.MQTT_TOPIC || 'factory/telemetry'
const INTERVAL_MS = parseInt(process.env.INTERVAL_MS || '5000')
const DEVICES = (process.env.DEVICES || 'sensor-1,sensor-2,sensor-3').split(',')

const client = mqtt.connect(BROKER_URL, {
  clientId: `simulator-${Date.now()}`,
  reconnectPeriod: 3000,
  connectTimeout: 10000
})

function randomInRange(min, max) {
  return parseFloat((Math.random() * (max - min) + min).toFixed(2))
}

function generatePayload(deviceId) {
  // Occasionally spike above threshold to trigger alerts
  const spike = Math.random() < 0.05
  return {
    deviceId,
    temperature: spike ? randomInRange(81, 95) : randomInRange(60, 78),
    humidity: randomInRange(35, 85),
    timestamp: Date.now()
  }
}

client.on('connect', () => {
  console.log(`[Simulator] Connected to ${BROKER_URL}`)
  console.log(`[Simulator] Publishing to topic: ${TOPIC}`)
  console.log(`[Simulator] Simulating devices: ${DEVICES.join(', ')}`)
  console.log(`[Simulator] Interval: ${INTERVAL_MS}ms`)

  DEVICES.forEach((deviceId, idx) => {
    const offset = idx * Math.floor(INTERVAL_MS / DEVICES.length)

    setTimeout(() => {
      const publish = () => {
        const payload = generatePayload(deviceId)
        const msg = JSON.stringify(payload)
        client.publish(TOPIC, msg, { qos: 1 }, (err) => {
          if (err) {
            console.error(`[Simulator][${deviceId}] Publish error: ${err.message}`)
          } else {
            console.log(`[Simulator][${deviceId}] temp=${payload.temperature}°C hum=${payload.humidity}%`)
          }
        })
      }

      publish()
      setInterval(publish, INTERVAL_MS)
    }, offset)
  })
})

client.on('error', (err) => {
  console.error('[Simulator] MQTT error:', err.message)
})

client.on('offline', () => {
  console.warn('[Simulator] MQTT connection lost, reconnecting...')
})

process.on('SIGINT', () => {
  console.log('[Simulator] Shutting down...')
  client.end()
  process.exit(0)
})
