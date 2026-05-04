import { motion } from 'framer-motion'
import type { CardView } from '../types'
import { getCardImageSrc, getDeckImageSrc } from '../utils/cardImage'

interface Props {
  card: CardView
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void
  disabled?: boolean
  dimmed?: boolean
  hidden?: boolean
  style?: React.CSSProperties
  className?: string
}

export function Card({ card, onClick, disabled, dimmed, hidden, style, className }: Props) {
  const src = getCardImageSrc(card)
  const interactive = !!onClick && !disabled

  return (
    // Hover only controls y-lift and scale — never touches rotate,
    // so the parent wrapper's tilt is always preserved.
    <motion.button
      className={[
        'card',
        interactive ? 'card--interactive' : '',
        dimmed ? 'card--dimmed' : '',
        hidden ? 'card--invisible' : '',
        className ?? '',
      ]
        .filter(Boolean)
        .join(' ')}
      onClick={interactive ? onClick : undefined}
      disabled={!interactive}
      style={style}
      aria-label={cardLabel(card)}
      whileHover={interactive ? { y: -20, scale: 1.1 } : undefined}
      whileTap={interactive ? { scale: 0.95 } : undefined}
      transition={{ type: 'spring', stiffness: 420, damping: 24 }}
    >
      <img src={src} alt={cardLabel(card)} draggable={false} />
    </motion.button>
  )
}

interface CardBackProps {
  index?: number
  total?: number
  style?: React.CSSProperties
}

export function CardBack({ index = 0, total = 1, style }: CardBackProps) {
  const spread = Math.min(total, 7)
  const rotatePerCard = spread > 1 ? Math.min(6, 20 / (spread - 1)) : 0
  const rotation = spread > 1 ? ((index - (spread - 1) / 2) * rotatePerCard) : 0
  const xOffset = spread > 1 ? ((index - (spread - 1) / 2) * 14) : 0
  return (
    <img
      src={getDeckImageSrc()}
      className="card-back"
      alt="Card back"
      draggable={false}
      style={{
        transform: `rotate(${rotation}deg) translateX(${xOffset}px)`,
        ...style,
      }}
    />
  )
}

function cardLabel(card: CardView): string {
  if (card.kind === 'WILD') {
    return card.wildType === 'WILD_DRAW_FOUR' ? 'Wild Draw Four' : 'Wild'
  }
  const color = card.color ? card.color.charAt(0) + card.color.slice(1).toLowerCase() : ''
  if (card.kind === 'NUMBER') return `${color} ${card.number}`
  if (card.action === 'DRAW_TWO') return `${color} Draw Two`
  if (card.action === 'SKIP') return `${color} Skip`
  return `${color} Reverse`
}
