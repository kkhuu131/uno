import { motion } from 'framer-motion'
import { useState } from 'react'
import { api } from '../api/client'

interface Props {
  onGameCreated: (gameId: string) => void
}

export function GameSetup({ onGameCreated }: Props) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleStart() {
    setLoading(true)
    setError(null)
    try {
      const { gameId } = await api.createGame()
      onGameCreated(gameId)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create game')
      setLoading(false)
    }
  }

  return (
    <div className="setup">
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

        <p className="setup__tagline">Party Edition 🎉</p>
        <div className="setup__divider" />

        {error && (
          <motion.p
            className="setup__error"
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
          >
            {error}
          </motion.p>
        )}

        <motion.button
          className="btn btn--start"
          onClick={handleStart}
          disabled={loading}
          whileHover={{ scale: 1.03 }}
          whileTap={{ scale: 0.97 }}
        >
          {loading ? 'Dealing cards…' : '🃏 Deal Cards'}
        </motion.button>

        <p className="setup__rule">
          2 players · hot seat · match color or number · first to empty your hand wins!
        </p>
      </motion.div>
    </div>
  )
}
