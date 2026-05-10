import { useEffect, useRef, useState, useCallback } from 'react'

export function useWebSocket(url) {
  const ws = useRef(null)
  const [lastMessage, setLastMessage] = useState(null)
  const [status, setStatus] = useState('DISCONNECTED')
  const reconnectTimeout = useRef(null)

  const connect = useCallback(() => {
    const wsUrl = url || `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws/telemetry`

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
