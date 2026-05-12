'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react'

const BASE_DELAY_MS = 1_000
const MAX_DELAY_MS  = 30_000
const JITTER_RATIO  = 0.3

function backoffDelay(attempt) {
  const exp    = Math.min(BASE_DELAY_MS * Math.pow(2, attempt), MAX_DELAY_MS)
  const jitter = exp * JITTER_RATIO * (Math.random() * 2 - 1)
  return Math.round(exp + jitter)
}

const WsContext = createContext({ lastMessage: null, status: 'DISCONNECTED' })

export function WebSocketProvider({ children }) {
  const ws           = useRef(null)
  const reconnectRef = useRef(null)
  const attemptRef   = useRef(0)
  const unmountedRef = useRef(false)

  const [lastMessage, setLastMessage] = useState(null)
  const [status, setStatus]           = useState('DISCONNECTED')

  const connect = useCallback(() => {
    if (unmountedRef.current) return
    const wsUrl =
      process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:8080/ws/telemetry'

    ws.current = new WebSocket(wsUrl)

    ws.current.onopen = () => {
      attemptRef.current = 0
      setStatus('CONNECTED')
    }

    ws.current.onmessage = (e) => {
      try { setLastMessage(JSON.parse(e.data)) }
      catch { setLastMessage(e.data) }
    }

    ws.current.onclose = () => {
      if (unmountedRef.current) return
      setStatus('RECONNECTING')
      const delay = backoffDelay(attemptRef.current++)
      reconnectRef.current = setTimeout(connect, delay)
    }

    ws.current.onerror = () => setStatus('ERROR')
  }, [])

  useEffect(() => {
    unmountedRef.current = false
    connect()
    return () => {
      unmountedRef.current = true
      clearTimeout(reconnectRef.current)
      ws.current?.close()
    }
  }, [connect])

  return (
    <WsContext.Provider value={{ lastMessage, status }}>
      {children}
    </WsContext.Provider>
  )
}

export const useWebSocket = () => useContext(WsContext)
