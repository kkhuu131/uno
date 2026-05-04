import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/client'
import type { CardView, GameSnapshot, PrivateHandUpdate } from '../types'
import { getSessionId } from '../utils/session'
import { useStompClient } from './useStompClient'

export function useGame(gameId: string, localPlayerIndex: number) {
  const [publicSnapshot, setPublicSnapshot] = useState<GameSnapshot | null>(null)
  const [privateHand, setPrivateHand] = useState<CardView[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const sessionId = getSessionId()

  // Initial load via REST so the table is not blank before WS connects
  useEffect(() => {
    api.getGame(gameId).then(setPublicSnapshot).catch(() => {})
  }, [gameId])

  // STOMP subscriptions
  useStompClient([
    {
      topic: `/topic/games/${gameId}`,
      onMessage: (body) => setPublicSnapshot(body as GameSnapshot),
    },
    {
      topic: `/topic/games/${gameId}/private/${sessionId}`,
      onMessage: (body) => {
        const update = body as PrivateHandUpdate
        setPrivateHand(update.hand)
      },
    },
  ])

  // Merge: local player gets the private hand; opponents keep their redacted state
  const snapshot = useMemo((): GameSnapshot | null => {
    if (!publicSnapshot) return null
    return {
      ...publicSnapshot,
      players: publicSnapshot.players.map((p, i) =>
        i === localPlayerIndex
          ? { ...p, hand: privateHand ?? [] }
          : { ...p, hand: p.hand ?? [] },
      ),
    }
  }, [publicSnapshot, privateHand, localPlayerIndex])

  const act = useCallback(
    async (fn: () => Promise<GameSnapshot | { game: GameSnapshot }>) => {
      if (busy) return
      setBusy(true)
      setError(null)
      try {
        const result = await fn()
        const snap = 'game' in result ? result.game : result
        setPublicSnapshot(snap)
        if (snap.players[localPlayerIndex].hand) {
          setPrivateHand(snap.players[localPlayerIndex].hand)
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Something went wrong')
      } finally {
        setBusy(false)
      }
    },
    [busy, localPlayerIndex],
  )

  const playCard = (playerIndex: number, handIndex: number, chosenColor?: string) =>
    act(() => api.playCard(gameId, playerIndex, handIndex, chosenColor))

  const drawCard = (playerIndex: number, endTurn?: boolean) =>
    act(() => api.drawCard(gameId, playerIndex, endTurn))

  const passTurn = (playerIndex: number) =>
    act(() => api.passTurn(gameId, playerIndex))

  return {
    snapshot,
    error,
    busy,
    clearError: () => setError(null),
    playCard,
    drawCard,
    passTurn,
  }
}
