import type { DrawCardResponse, GameSnapshot, LobbySnapshot } from '../types'
import { getSessionId } from '../utils/session'

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? 'http://localhost:8080/api'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Session-Id': getSessionId(),
      ...(options?.headers ?? {}),
    },
    ...options,
  })
  if (!res.ok) {
    let message = res.statusText
    try {
      const body = await res.json() as { message?: string }
      if (body.message) message = body.message
    } catch {
      // leave statusText
    }
    throw new Error(message)
  }
  return res.json() as Promise<T>
}

export const api = {
  // ── Game (existing) ──────────────────────────────────────────────────
  createGame: () =>
    request<{ gameId: string }>('/games', { method: 'POST' }),

  getGame: (id: string) =>
    request<GameSnapshot>(`/games/${id}`),

  playCard: (id: string, playerIndex: number, handIndex: number, chosenColor?: string) =>
    request<GameSnapshot>(`/games/${id}/play`, {
      method: 'POST',
      body: JSON.stringify({ playerIndex, handIndex, chosenColor: chosenColor ?? null }),
    }),

  drawCard: (id: string, playerIndex: number, endTurn?: boolean) =>
    request<DrawCardResponse>(`/games/${id}/draw`, {
      method: 'POST',
      body: JSON.stringify({ playerIndex, endTurn: endTurn ?? null }),
    }),

  passTurn: (id: string, playerIndex: number) =>
    request<GameSnapshot>(`/games/${id}/pass`, {
      method: 'POST',
      body: JSON.stringify({ playerIndex }),
    }),

  // ── Lobby (new) ──────────────────────────────────────────────────────
  createLobby: (displayName: string) =>
    request<{ code: string; playerIndex: number; lobby: LobbySnapshot }>('/lobbies', {
      method: 'POST',
      body: JSON.stringify({ displayName }),
    }),

  joinLobby: (code: string, displayName: string) =>
    request<{ playerIndex: number; lobby: LobbySnapshot }>(`/lobbies/${code}/join`, {
      method: 'POST',
      body: JSON.stringify({ displayName }),
    }),

  startGame: (code: string) =>
    request<{ gameId: string }>(`/lobbies/${code}/start`, { method: 'POST' }),

  getLobby: (code: string) =>
    request<LobbySnapshot>(`/lobbies/${code}`),
}
