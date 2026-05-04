import type { PlayerStateView } from '../types'
import { CardBack } from './Card'

interface Props {
  player: PlayerStateView
  isActive: boolean
  style: React.CSSProperties
}

const CARD_W = 52
const CARD_H = 74
const MAX_FAN_W   = 150   // px: widest the opponent fan can grow
const FAN_STEP_MAX = CARD_W * 0.55  // ~28 px natural step
const FAN_STEP_MIN = 8    // always show a sliver of each extra card

export function OpponentSlot({ player, isActive, style }: Props) {
  const displayCount = Math.min(player.handSize, 12)
  const fanStep = displayCount <= 1
    ? 0
    : Math.max(FAN_STEP_MIN, Math.min(FAN_STEP_MAX, (MAX_FAN_W - CARD_W) / (displayCount - 1)))
  const containerWidth = displayCount > 0 ? fanStep * (displayCount - 1) + CARD_W : CARD_W

  return (
    <div
      className={`opponent-slot${isActive ? ' opponent-slot--active' : ''}`}
      style={style}
    >
      <div className="opponent-slot__name">{player.name}</div>

      <div className="opponent-slot__fan" style={{ width: containerWidth, height: CARD_H }}>
        {Array.from({ length: displayCount }).map((_, i) => (
          <CardBack
            key={i}
            index={i}
            total={displayCount}
            style={{ position: 'absolute', left: i * fanStep, width: CARD_W, height: CARD_H }}
          />
        ))}
      </div>

      <div className="opponent-slot__badge">{player.handSize}</div>
    </div>
  )
}
