import type { PlayerStateView } from '../types'
import { CardBack } from './Card'

interface Props {
  player: PlayerStateView
  isActive: boolean
  style: React.CSSProperties
}

const CARD_W = 52
const CARD_H = 74

export function OpponentSlot({ player, isActive, style }: Props) {
  const displayCount = Math.min(player.handSize, 7)
  const fanWidth = displayCount > 1 ? (CARD_W * 0.55) : 0
  const containerWidth = displayCount > 0 ? fanWidth * (displayCount - 1) + CARD_W : CARD_W

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
            style={{ position: 'absolute', left: i * fanWidth, width: CARD_W, height: CARD_H }}
          />
        ))}
      </div>

      <div className="opponent-slot__badge">{player.handSize}</div>
    </div>
  )
}
