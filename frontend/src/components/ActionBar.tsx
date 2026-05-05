import { AnimatePresence, motion } from 'framer-motion'
import { ChevronsRight, Layers, Skull, Zap } from 'lucide-react'

interface Props {
  playerIndex: number
  pendingDrawStack: boolean
  hasDrawnThisTurn: boolean
  noPlayableCards: boolean
  busy: boolean
  onDraw: () => void
  onPass: () => void
}

const hintVariants = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0 },
  exit:    { opacity: 0, y: -8 },
}

const springTransition = { type: 'spring', stiffness: 350, damping: 25 } as const

export function ActionBar({ pendingDrawStack, hasDrawnThisTurn, noPlayableCards, busy, onDraw, onPass }: Props) {
  const drawDisabled = busy || pendingDrawStack || hasDrawnThisTurn
  const passDisabled = busy || (!pendingDrawStack && !hasDrawnThisTurn)

  return (
    <motion.div
      className="action-bar"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'spring', stiffness: 300, damping: 22 }}
    >
      <AnimatePresence mode="wait">
        {pendingDrawStack && (
          <motion.p
            key="hint-stack"
            className="action-bar__hint action-bar__hint--warn"
            variants={hintVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            transition={springTransition}
          >
            <Zap size={13} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 4 }} />
            Stack a +2/+4 or <strong>Pass</strong> to take the penalty!
          </motion.p>
        )}
        {!pendingDrawStack && noPlayableCards && !hasDrawnThisTurn && (
          <motion.p
            key="hint-no-playable"
            className="action-bar__hint action-bar__hint--warn"
            variants={hintVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            transition={springTransition}
          >
            No playable cards — <strong>Draw</strong> until you find one!
          </motion.p>
        )}
        {hasDrawnThisTurn && !pendingDrawStack && (
          <motion.p
            key="hint-drawn"
            className="action-bar__hint"
            variants={hintVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            transition={springTransition}
          >
            Card drawn! Play it or <strong>End Turn</strong>.
          </motion.p>
        )}
      </AnimatePresence>

      <div className="action-bar__buttons">
        <motion.button
          className={`btn btn--draw${busy && drawDisabled ? ' btn--loading' : ''}`}
          onClick={onDraw}
          disabled={drawDisabled}
          whileHover={drawDisabled ? {} : { scale: 1.04 }}
          whileTap={drawDisabled ? {} : { scale: 0.95 }}
          title={pendingDrawStack ? 'Resolve the draw stack first' : undefined}
        >
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Layers size={15} /> Draw
          </span>
        </motion.button>

        <motion.button
          className="btn btn--pass"
          onClick={onPass}
          disabled={passDisabled}
          whileHover={passDisabled ? {} : { scale: 1.04 }}
          whileTap={passDisabled ? {} : { scale: 0.95 }}
        >
          <AnimatePresence mode="wait">
            {pendingDrawStack ? (
              <motion.span
                key="take-stack"
                style={{ display: 'flex', alignItems: 'center', gap: 6 }}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -6 }}
                transition={{ duration: 0.15 }}
              >
                <Skull size={15} /> Take Stack
              </motion.span>
            ) : (
              <motion.span
                key="end-turn"
                style={{ display: 'flex', alignItems: 'center', gap: 6 }}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -6 }}
                transition={{ duration: 0.15 }}
              >
                <ChevronsRight size={15} /> End Turn
              </motion.span>
            )}
          </AnimatePresence>
        </motion.button>
      </div>
    </motion.div>
  )
}
