import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { GameSnapshot } from '../types'

export function useGame(gameId: string) {
  const [snapshot, setSnapshot] = useState<GameSnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const activeRef = useRef(true)

  const fetchSnapshot = useCallback(async () => {
    try {
      const data = await api.getGame(gameId)
      if (activeRef.current) setSnapshot(data)
    } catch {
      // poll errors are silent; action errors surface separately
    }
  }, [gameId])

  useEffect(() => {
    activeRef.current = true
    fetchSnapshot()
    const id = setInterval(fetchSnapshot, 1000)
    return () => {
      activeRef.current = false
      clearInterval(id)
    }
  }, [fetchSnapshot])

  const act = useCallback(
    async (fn: () => Promise<GameSnapshot | { game: GameSnapshot }>) => {
      if (busy) return
      setBusy(true)
      setError(null)
      try {
        const result = await fn()
        const snap = 'game' in result ? result.game : result
        setSnapshot(snap)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Something went wrong')
      } finally {
        setBusy(false)
      }
    },
    [busy],
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
