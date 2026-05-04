import { motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { LobbySnapshot } from '../types'
import { getSessionId } from '../utils/session'
import { useStompClient } from '../hooks/useStompClient'
import { UsernameBar } from './UsernameBar'

export function LobbyRoom() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const [lobby, setLobby] = useState<LobbySnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const [copied, setCopied] = useState(false)

  const sessionId = getSessionId()

  // Initial REST load
  useEffect(() => {
    if (!code) return
    api.getLobby(code)
      .then(setLobby)
      .catch(() => setError('Lobby not found'))
  }, [code])

  // Live updates via STOMP
  useStompClient(
    code
      ? [{ topic: `/topic/lobbies/${code}`, onMessage: (body) => {
          const snap = body as LobbySnapshot
          setLobby(snap)
          if (snap.status === 'IN_PROGRESS' && snap.gameId) {
            navigate('/game/' + snap.gameId, {
              state: {
                playerIndex: snap.players.find(
                  (p) => p.playerIndex === parseInt(sessionStorage.getItem('uno_player_index') ?? '-1')
                )?.playerIndex ?? 0,
              },
            })
          }
        }}]
      : []
  )

  function copyCode() {
    if (!code) return
    navigator.clipboard.writeText(code).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  async function handleStart() {
    if (!code) return
    setStarting(true)
    setError(null)
    try {
      const { gameId } = await api.startGame(code)
      const playerIndex = parseInt(sessionStorage.getItem('uno_player_index') ?? '0')
      navigate('/game/' + gameId, { state: { playerIndex } })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to start game')
      setStarting(false)
    }
  }

  if (!lobby) {
    return <div className="lobby-loading">{error ?? 'Loading lobby…'}</div>
  }

  const myPlayerIndex = parseInt(sessionStorage.getItem('uno_player_index') ?? '-1')
  const isHost = lobby.hostPlayerIndex === myPlayerIndex

  return (
    <div className="lobby">
      <UsernameBar />

      <motion.div
        className="lobby__card"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <h1 className="lobby__title">Waiting for players…</h1>

        <div className="lobby__code-row">
          <span className="lobby__code">{code}</span>
          <button className="btn btn--copy" onClick={copyCode}>
            {copied ? 'Copied!' : 'Copy'}
          </button>
        </div>

        <p className="lobby__hint">Share this code with friends to invite them</p>

        <ul className="lobby__players">
          {lobby.players.map((p) => (
            <motion.li
              key={p.playerIndex}
              className="lobby__player"
              initial={{ opacity: 0, x: -12 }}
              animate={{ opacity: 1, x: 0 }}
            >
              {p.playerIndex === lobby.hostPlayerIndex && (
                <span className="lobby__host-crown">♛</span>
              )}
              {p.displayName}
            </motion.li>
          ))}
        </ul>

        {error && <p className="lobby__error">{error}</p>}

        {isHost && (
          <motion.button
            className="btn btn--start"
            onClick={handleStart}
            disabled={starting || lobby.players.length < 2}
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.97 }}
          >
            {starting
              ? 'Starting…'
              : lobby.players.length < 2
              ? 'Waiting for players…'
              : `Start Game (${lobby.players.length} players)`}
          </motion.button>
        )}

        {!isHost && (
          <p className="lobby__waiting">Waiting for the host to start the game…</p>
        )}
      </motion.div>
    </div>
  )
}
