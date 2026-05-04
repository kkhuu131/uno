import { motion } from 'framer-motion'
import type { PlayerStateView } from '../types'
import { CardBack } from './Card'

interface Props {
  player: PlayerStateView
  isActive: boolean
  style: React.CSSProperties
}

const CARD_W = 52
const CARD_H = 74
const MAX_ROW_W = 200
const GAP_DEFAULT = Math.round(CARD_W * 0.55)  // ~28px visible strip
const GAP_MIN = 6

function rowGap(count: number): number {
  if (count <= 1) return 0
  const ideal = (MAX_ROW_W - CARD_W) / (count - 1)
  return Math.max(GAP_MIN, Math.min(GAP_DEFAULT, ideal))
}

export function OpponentSlot({ player, isActive, style }: Props) {
  const count = Math.min(player.handSize, 12)
  const gap = rowGap(count)
  const rotatePerCard = count <= 1 ? 2.5 : Math.min(2.5, 12 / (count - 1))
  const tiltPerCard = rotatePerCard * 0.7

  return (
    <div
      className={`opponent-slot${isActive ? ' opponent-slot--active' : ''}`}
      style={style}
    >
      <div className="opponent-slot__name">{player.name}</div>

      <div className="opponent-slot__fan">
        {Array.from({ length: count }).map((_, i) => {
          const mid = (count - 1) / 2
          const offset = i - mid
          const rotate = offset * rotatePerCard
          const tiltY = Math.abs(offset) * tiltPerCard

          return (
            <motion.div
              key={i}
              animate={{ y: tiltY, rotate }}
              style={{
                flexShrink: 0,
                marginLeft: i > 0 ? gap - CARD_W : 0,
                transformOrigin: 'bottom center',
              }}
            >
              <CardBack style={{ width: CARD_W, height: CARD_H }} />
            </motion.div>
          )
        })}
      </div>

      <div className="opponent-slot__badge">{player.handSize}</div>
    </div>
  )
}
