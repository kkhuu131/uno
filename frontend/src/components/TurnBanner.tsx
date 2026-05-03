import { AnimatePresence, motion } from 'framer-motion'

interface Props {
  visible: boolean
  name: string
}

export function TurnBanner({ visible, name }: Props) {
  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          className="turn-banner"
          initial={{ y: -64, opacity: 0, scale: 0.85 }}
          animate={{ y: 0,   opacity: 1, scale: 1 }}
          exit={{   y: -64, opacity: 0, scale: 0.85 }}
          transition={{ type: 'spring', stiffness: 380, damping: 28 }}
        >
          {name}&apos;s Turn! 🎯
        </motion.div>
      )}
    </AnimatePresence>
  )
}
