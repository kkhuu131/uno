import { motion } from 'framer-motion'

interface Props {
  playerIndex: number
  pendingDrawStack: boolean
  hasDrawnThisTurn: boolean
  noPlayableCards: boolean
  busy: boolean
  onDraw: () => void
  onPass: () => void
}

export function ActionBar({ pendingDrawStack, hasDrawnThisTurn, noPlayableCards, busy, onDraw, onPass }: Props) {
  const drawDisabled  = busy || pendingDrawStack || hasDrawnThisTurn
  const passDisabled  = busy || (!pendingDrawStack && !hasDrawnThisTurn)

  return (
    <motion.div
      className="action-bar"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'spring', stiffness: 300, damping: 22 }}
    >
      {pendingDrawStack && (
        <p className="action-bar__hint action-bar__hint--warn">
          ⚡ Stack a +2/+4 or <strong>Pass</strong> to take the penalty!
        </p>
      )}
      {!pendingDrawStack && noPlayableCards && !hasDrawnThisTurn && (
        <p className="action-bar__hint action-bar__hint--warn">
          No playable cards — <strong>Draw</strong> until you find one!
        </p>
      )}
      {hasDrawnThisTurn && !pendingDrawStack && (
        <p className="action-bar__hint">
          Card drawn! Play it or <strong>End Turn</strong>.
        </p>
      )}

      <div className="action-bar__buttons">
        <button
          className="btn btn--draw"
          onClick={onDraw}
          disabled={drawDisabled}
          title={pendingDrawStack ? 'Resolve the draw stack first' : undefined}
        >
          🃏 Draw
        </button>

        <button
          className="btn btn--pass"
          onClick={onPass}
          disabled={passDisabled}
        >
          {pendingDrawStack ? '💀 Take Stack' : '⏭ End Turn'}
        </button>
      </div>
    </motion.div>
  )
}
