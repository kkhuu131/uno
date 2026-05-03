import { AnimatePresence, motion } from 'framer-motion'
import type { CardView } from '../types'
import { getCardImageSrc, getDeckImageSrc } from '../utils/cardImage'

export interface FlyingCardData {
  id: string
  /** null = show deck back (draw animation); defined = show card face (play animation) */
  card: CardView | null
  from: { x: number; y: number; w: number; h: number }
  to:   { x: number; y: number; w: number; h: number }
  onComplete?: () => void
}

interface Props {
  cards: FlyingCardData[]
}

export function FlyingCardOverlay({ cards }: Props) {
  return (
    <div className="flying-card-overlay">
      <AnimatePresence>
        {cards.map(fc => (
          <FlyingCardItem key={fc.id} fc={fc} />
        ))}
      </AnimatePresence>
    </div>
  )
}

function FlyingCardItem({ fc }: { fc: FlyingCardData }) {
  const isDraw = fc.card === null

  return (
    <motion.div
      initial={{
        position: 'fixed' as const,
        x: fc.from.x,
        y: fc.from.y,
        width: fc.from.w,
        height: fc.from.h,
        rotate: 0,
        scale: 1,
        zIndex: 9999,
      }}
      animate={{
        x: fc.to.x,
        y: fc.to.y,
        width: fc.to.w,
        height: fc.to.h,
        rotate: isDraw ? [0, -12, 6] : [0, 15, -5],
        scale: [1, 1.12, 0.97],
      }}
      exit={{ opacity: 0, scale: 0.8 }}
      transition={{
        duration: 0.42,
        ease: [0.25, 0.46, 0.45, 0.94],
        rotate: { duration: 0.42, ease: 'easeInOut' },
        scale: { duration: 0.42, ease: 'easeInOut' },
      }}
      onAnimationComplete={fc.onComplete}
      style={{ position: 'fixed', top: 0, left: 0 }}
    >
      {isDraw ? (
        /* Draw: show deck back, flip to reveal mid-flight */
        <>
          <motion.img
            className="flying-card-img"
            src={getDeckImageSrc()}
            alt="Drawing"
            animate={{ rotateY: [0, 90, 90] }}
            transition={{ duration: 0.42, times: [0, 0.45, 1], ease: 'easeInOut' }}
            style={{ position: 'absolute', inset: 0, backfaceVisibility: 'hidden' }}
          />
          <motion.img
            className="flying-card-img"
            src={getDeckImageSrc()}
            alt="Drawing"
            animate={{ rotateY: [90, 90, 0] }}
            transition={{ duration: 0.42, times: [0, 0.45, 1], ease: 'easeInOut' }}
            style={{ position: 'absolute', inset: 0, backfaceVisibility: 'hidden' }}
          />
        </>
      ) : (
        <img
          className="flying-card-img"
          src={getCardImageSrc(fc.card!)}
          alt="Playing card"
        />
      )}
    </motion.div>
  )
}
