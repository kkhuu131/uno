import { motion } from 'framer-motion'
import { AutoConfetti } from './Confetti'

interface Props {
  winnerName: string
  onNewGame: () => void
  onPlayAgain?: () => void
  onLeaveLobby?: () => void
}

export function GameOver({ winnerName, onNewGame, onPlayAgain, onLeaveLobby }: Props) {
  return (
    <>
      <AutoConfetti active />
      <div className="gameover-overlay">
        <motion.div
          className="gameover"
          initial={{ scale: 0.6, opacity: 0, y: 32 }}
          animate={{ scale: 1, opacity: 1, y: 0 }}
          transition={{ type: 'spring', stiffness: 300, damping: 22 }}
        >
          <motion.div
            className="gameover__trophy"
            animate={{ rotate: [-8, 8, -8] }}
            transition={{ repeat: Infinity, duration: 2, ease: 'easeInOut' }}
          >
            🏆
          </motion.div>

          <h1 className="gameover__title">Game Over!</h1>

          <div className="gameover__winner">
            <span className="gameover__winner-label">Winner</span>
            <motion.span
              className="gameover__winner-name"
              initial={{ scale: 0.7 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', stiffness: 280, damping: 16, delay: 0.15 }}
            >
              🎉 {winnerName}
            </motion.span>
          </div>

          {onPlayAgain && onLeaveLobby ? (
            <div className="gameover__actions">
              <motion.button
                className="btn btn--newgame"
                onClick={onPlayAgain}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.96 }}
              >
                Play Again
              </motion.button>
              <motion.button
                className="btn btn--leave"
                onClick={onLeaveLobby}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.96 }}
              >
                Leave Lobby
              </motion.button>
            </div>
          ) : (
            <motion.button
              className="btn btn--newgame"
              onClick={onNewGame}
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.96 }}
            >
              New Game
            </motion.button>
          )}
        </motion.div>
      </div>
    </>
  )
}
