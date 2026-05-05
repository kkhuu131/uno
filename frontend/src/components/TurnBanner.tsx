import { AnimatePresence, motion } from 'framer-motion'

interface Props {
  visible: boolean
  name: string
  isMyTurn: boolean
}

export function TurnBanner({ visible, name, isMyTurn }: Props) {
  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          className={`turn-banner${isMyTurn ? ' turn-banner--mine' : ''}`}
          initial={{ y: -64, opacity: 0, scale: 0.85 }}
          animate={{ y: 0, opacity: 1, scale: 1 }}
          exit={{ y: -64, opacity: 0, scale: 0.85 }}
          transition={{ type: 'spring', stiffness: 500, damping: 28 }}
        >
          {isMyTurn ? 'Your Turn! 🎯' : `${name}'s Turn! 🎯`}
        </motion.div>
      )}
    </AnimatePresence>
  )
}
