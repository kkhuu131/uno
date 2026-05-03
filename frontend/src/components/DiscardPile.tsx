import { AnimatePresence, motion } from 'framer-motion'
import type { CardColor, CardView } from '../types'
import { getCardImageSrc } from '../utils/cardImage'

export interface DiscardEntry {
  id: number
  card: CardView
}

interface Props {
  history: DiscardEntry[]
  activeColor: CardColor
  pileRef?: React.Ref<HTMLDivElement>
}

// Fixed per-slot styles: index 0 = top of pile (newest), higher = deeper in pile
const PILE_SLOTS = [
  { rotate:  5,  x:  0,  y:  0 },   // newest — on top
  { rotate: -14, x: -5,  y:  2 },
  { rotate:  9,  x:  6,  y:  1 },
  { rotate: -11, x: -3,  y:  3 },
  { rotate:  15, x:  4,  y:  2 },
  { rotate:  -7, x: -7,  y:  4 },
]

const COLOR_CLASS: Record<CardColor, string> = {
  RED:    'active-color--RED',
  YELLOW: 'active-color--YELLOW',
  GREEN:  'active-color--GREEN',
  BLUE:   'active-color--BLUE',
}

export function DiscardPile({ history, activeColor, pileRef }: Props) {
  return (
    <div className="discard-pile" ref={pileRef}>
      <AnimatePresence>
        {history.slice(0, PILE_SLOTS.length).map((entry, i) => {
          const slot = PILE_SLOTS[i]
          // zIndex: newest (i=0) is highest
          const zIndex = PILE_SLOTS.length - i

          return (
            <motion.div
              key={entry.id}
              className="discard-pile__card"
              style={{ zIndex }}
              // New cards land with a satisfying bounce
              initial={i === 0 ? { scale: 1.3, y: -28, rotate: slot.rotate - 8 } : false}
              animate={{
                scale: 1,
                x: slot.x,
                y: slot.y,
                rotate: slot.rotate,
              }}
              transition={{
                type: 'spring',
                stiffness: 340,
                damping: 22,
              }}
            >
              <img
                src={getCardImageSrc(entry.card)}
                alt={cardLabel(entry.card)}
                draggable={false}
              />
            </motion.div>
          )
        })}
      </AnimatePresence>

      {/* Active color dot — always on top */}
      <div
        className={`active-color-badge ${COLOR_CLASS[activeColor]}`}
        title={`Active color: ${activeColor}`}
        style={{ zIndex: 20 }}
      />
    </div>
  )
}

function cardLabel(card: CardView): string {
  if (card.kind === 'WILD') return card.wildType === 'WILD_DRAW_FOUR' ? 'Wild +4' : 'Wild'
  const c = card.color ? card.color[0] + card.color.slice(1).toLowerCase() : ''
  if (card.kind === 'NUMBER') return `${c} ${card.number}`
  if (card.action === 'DRAW_TWO') return `${c} +2`
  if (card.action === 'SKIP') return `${c} Skip`
  return `${c} Reverse`
}
