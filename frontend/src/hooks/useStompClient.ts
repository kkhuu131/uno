import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useEffect, useRef, useState } from 'react'

const WS_BASE =
  (import.meta.env.VITE_WS_BASE as string | undefined) ?? 'http://localhost:8080/ws'

export interface StompSubscription {
  topic: string
  onMessage: (body: unknown) => void
}

export function useStompClient(subscriptions: StompSubscription[]) {
  const [connected, setConnected] = useState(false)
  const subscriptionsRef = useRef(subscriptions)
  subscriptionsRef.current = subscriptions

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_BASE) as WebSocket,
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        subscriptionsRef.current.forEach(({ topic, onMessage }) => {
          client.subscribe(topic, (msg) => {
            try {
              onMessage(JSON.parse(msg.body))
            } catch {
              // ignore malformed frames
            }
          })
        })
      },
      onDisconnect: () => setConnected(false),
    })
    client.activate()
    return () => {
      client.deactivate()
    }
  }, []) // connect once per mount; subscriptions are read from ref

  return { connected }
}
