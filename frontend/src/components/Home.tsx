import { motion } from 'framer-motion'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { getDisplayName, setDisplayName } from '../utils/session'
import { UsernameBar } from './UsernameBar'

export function Home() {
  const navigate = useNavigate()
  const [joinCode, setJoinCode] = useState('')
  const [loading, setLoading] = useState<'create' | 'join' | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleCreate() {
    const name = getDisplayName()
    if (!name) { setError('Set your name first'); return }
    setLoading('create')
    setError(null)
    try {
      const { code, playerIndex } = await api.createLobby(name)
      sessionStorage.setItem('uno_player_index', String(playerIndex))
      navigate('/lobby/' + code)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create lobby')
      setLoading(null)
    }
  }

  async function handleJoin() {
    const name = getDisplayName()
    if (!name) { setError('Set your name first'); return }
    const code = joinCode.trim().toUpperCase()
    if (!code) { setError('Enter a lobby code'); return }
    setLoading('join')
    setError(null)
    try {
      const { playerIndex } = await api.joinLobby(code, name)
      sessionStorage.setItem('uno_player_index', String(playerIndex))
      navigate('/lobby/' + code)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to join lobby')
      setLoading(null)
    }
  }

  return (
    <div className="setup">
      <UsernameBar onChange={() => setDisplayName(getDisplayName())} />

      <motion.div
        className="setup__card"
        initial={{ scale: 0.8, opacity: 0, y: 24 }}
        animate={{ scale: 1, opacity: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 320, damping: 24 }}
      >
        <motion.div
          className="setup__logo"
          initial={{ scale: 0.5, rotate: -12 }}
          animate={{ scale: 1, rotate: 0 }}
          transition={{ type: 'spring', stiffness: 260, damping: 18, delay: 0.1 }}
        >
          UNO
        </motion.div>

        {error && (
          <motion.p className="setup__error" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
            {error}
          </motion.p>
        )}

        <motion.button
          className="btn btn--start"
          onClick={handleCreate}
          disabled={!!loading}
          whileHover={{ scale: 1.03 }}
          whileTap={{ scale: 0.97 }}
        >
          {loading === 'create' ? 'Creating…' : '+ Create Game'}
        </motion.button>

        <div className="setup__divider" />

        <div className="setup__join">
          <input
            className="setup__join-input"
            placeholder="Lobby code (e.g. UNO-7X3K)"
            value={joinCode}
            maxLength={8}
            onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
            onKeyDown={(e) => { if (e.key === 'Enter') handleJoin() }}
          />
          <motion.button
            className="btn btn--join"
            onClick={handleJoin}
            disabled={!!loading}
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.97 }}
          >
            {loading === 'join' ? 'Joining…' : 'Join Game'}
          </motion.button>
        </div>
      </motion.div>
    </div>
  )
}
