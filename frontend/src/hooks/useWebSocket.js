'use client'

import { useEffect, useRef, useState, useCallback } from 'react'

const BASE_DELAY_MS  = 1_000   // initial reconnect delay
const MAX_DELAY_MS   = 30_000  // cap at 30 s
const JITTER_RATIO   = 0.3     // ±30% jitter to avoid thundering-herd on server restart

function backoffDelay(attempt) {
  const exponential = Math.min(BASE_DELAY_MS * Math.pow(2, attempt), MAX_DELAY_MS)
  const jitter = exponential * JITTER_RATIO * (Math.random() * 2 - 1)
  return Math.round(exponential + jitter)
}

export function useWebSocket(url) {
  const ws              = useRef(null)
  const reconnectRef    = useRef(null)
  const attemptRef      = useRef(0)
  const unmountedRef    = useRef(false)

  const [lastMessage, setLastMessage] = useState(null)
  const [status, setStatus]           = useState('DISCONNECTED')

  const connect = useCallback(() => {
    if (unmountedRef.current) return

    // NEXT_PUBLIC_WS_URL is set in docker-compose for production
    const wsUrl = url
      || process.env.NEXT_PUBLIC_WS_URL
      || 'ws://localhost:8080/ws/telemetry'

    ws.current = new WebSocket(wsUrl)

    ws.current.onopen = () => {
      attemptRef.current = 0   // reset backoff counter on successful connect
      setStatus('CONNECTED')
    }

    ws.current.onmessage = (event) => {
      try {
        setLastMessage(JSON.parse(event.data))
      } catch {
        setLastMessage(event.data)
      }
    }

    ws.current.onclose = () => {
      if (unmountedRef.current) return
      setStatus('RECONNECTING')
      const delay = backoffDelay(attemptRef.current)
      attemptRef.current += 1
      reconnectRef.current = setTimeout(connect, delay)
    }

    ws.current.onerror = () => {
      setStatus('ERROR')
      // onclose fires after onerror — reconnect is handled there
    }
  }, [url])

  useEffect(() => {
    unmountedRef.current = false
    connect()
    return () => {
      unmountedRef.current = true
      if (reconnectRef.current) clearTimeout(reconnectRef.current)
      ws.current?.close()
    }
  }, [connect])

  return { lastMessage, status }
}
