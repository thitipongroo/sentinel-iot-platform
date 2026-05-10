'use strict'

const mqtt = require('mqtt')

const BROKER_URL = process.env.MQTT_BROKER || 'mqtt://localhost:1883'
const TOPIC = process.env.MQTT_TOPIC || 'factory/telemetry'
const INTERVAL_MS = parseInt(process.env.INTERVAL_MS || '5000')
const DEVICES = (process.env.DEVICES || 'sensor-1,sensor-2,sensor-3').split(',')

const connectOpts = {
  clientId: `simulator-${Date.now()}`,
  reconnectPeriod: 3000,
  connectTimeout: 10000,
}
if (process.env.MQTT_USER) {
  connectOpts.username = process.env.MQTT_USER
  connectOpts.password = process.env.MQTT_PASS
}

const client = mqtt.connect(BROKER_URL, connectOpts)

function randomInRange(min, max) {
  return parseFloat((Math.random() * (max - min) + min).toFixed(2))
}

function generatePayload(deviceId) {
  // ~5% chance of temperature spike above threshold
  const tempSpike = Math.random() < 0.05
  // ~3% chance of smoke spike above threshold
  const smokeSpike = Math.random() < 0.03
  // ~20% chance of motion detection
  const motionDetected = Math.random() < 0.20

  return {
    deviceId,
    temperature: tempSpike ? randomInRange(81, 95) : randomInRange(60, 78),
    humidity: randomInRange(35, 85),
    motion: motionDetected,
    smokePpm: smokeSpike ? randomInRange(201, 350) : randomInRange(5, 50),
    timestamp: Date.now()
  }
}

client.on('connect', () => {
  console.log(`[Simulator] Connected to ${BROKER_URL}`)
  console.log(`[Simulator] Topic    : ${TOPIC}`)
  console.log(`[Simulator] Devices  : ${DEVICES.join(', ')}`)
  console.log(`[Simulator] Sensors  : temperature, humidity, motion, smoke`)
  console.log(`[Simulator] Interval : ${INTERVAL_MS}ms`)

  DEVICES.forEach((deviceId, idx) => {
    // Stagger device publishes to spread MQTT load
    const offset = idx * Math.floor(INTERVAL_MS / DEVICES.length)

    setTimeout(() => {
      const publish = () => {
        const payload = generatePayload(deviceId)
        const msg = JSON.stringify(payload)
        client.publish(TOPIC, msg, { qos: 1 }, (err) => {
          if (err) {
            console.error(`[${deviceId}] Publish error: ${err.message}`)
          } else {
            console.log(
              `[${deviceId}] temp=${payload.temperature}°C ` +
              `hum=${payload.humidity}% ` +
              `motion=${payload.motion ? 'YES' : 'no'} ` +
              `smoke=${payload.smokePpm}ppm`
            )
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
  console.warn('[Simulator] Disconnected, reconnecting...')
})

process.on('SIGINT', () => {
  console.log('[Simulator] Shutting down...')
  client.end()
  process.exit(0)
})
