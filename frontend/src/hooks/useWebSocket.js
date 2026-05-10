'use client'

import { useEffect, useRef, useState, useCallback } from 'react'

export function useWebSocket(url) {
  const ws = useRef(null)
  const [lastMessage, setLastMessage] = useState(null)
  const [status, setStatus] = useState('DISCONNECTED')
  const reconnectTimeout = useRef(null)

  const connect = useCallback(() => {
    // NEXT_PUBLIC_WS_URL is set in docker-compose for production (ws://localhost:8080/ws/telemetry)
    const wsUrl = url
      || process.env.NEXT_PUBLIC_WS_URL
      || 'ws://localhost:8080/ws/telemetry'

    ws.current = new WebSocket(wsUrl)

    ws.current.onopen = () => {
      setStatus('CONNECTED')
      if (reconnectTimeout.current) clearTimeout(reconnectTimeout.current)
    }

    ws.current.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        setLastMessage(data)
      } catch {
        setLastMessage(event.data)
      }
    }

    ws.current.onclose = () => {
      setStatus('RECONNECTING')
      reconnectTimeout.current = setTimeout(connect, 3000)
    }

    ws.current.onerror = () => {
      setStatus('ERROR')
    }
  }, [url])

  useEffect(() => {
    connect()
    return () => {
      if (reconnectTimeout.current) clearTimeout(reconnectTimeout.current)
      ws.current?.close()
    }
  }, [connect])

  return { lastMessage, status }
}
