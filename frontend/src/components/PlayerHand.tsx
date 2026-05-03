import { motion } from 'framer-motion'
import { useRef } from 'react'
import type { CardView } from '../types'
import { Card } from './Card'

interface Props {
  hand: CardView[]
  playerIndex: number
  isCurrentPlayer: boolean
  flipped?: boolean
  onPlayCard?: (handIndex: number, card: CardView, event: React.MouseEvent<HTMLButtonElement>) => void
  busy?: boolean
  hiddenHandIndex?: number | null
  handRef?: React.Ref<HTMLDivElement>
}

export function PlayerHand({
  hand,
  isCurrentPlayer,
  flipped,
  onPlayCard,
  busy,
  hiddenHandIndex,
  handRef,
}: Props) {
  const count = hand.length

  return (
    // Outer scroll wrapper: scrolls horizontally but does NOT create overflow-y clipping.
    // (Setting overflow-x:auto forces overflow-y:auto in CSS — we avoid that here
    //  so card hover-lifts and tilts are never cut off.)
    <div className={`hand-scroll ${flipped ? 'hand-scroll--flipped' : ''}`} ref={handRef}>
      <div className="hand">
        {hand.map((card, i) => {
          const mid = (count - 1) / 2
          const offset = i - mid
          const rotate = offset * 2.8
          const tiltY = Math.abs(offset) * 2.4

          return (
            // The wrapper owns the tilt (rotate + y-arc) so that the card button's
            // whileHover can freely animate its own y/scale WITHOUT ever touching
            // the wrapper's rotate — the tilt is therefore always preserved.
            <TiltWrapper
              key={`${i}-${card.kind}-${card.color}-${card.number}-${card.action}-${card.wildType}`}
              rotate={rotate}
              tiltY={tiltY}
              baseZIndex={i}
              delay={i * 0.04}
              marginLeft={i > 0 ? -20 : 0}
            >
              <Card
                card={card}
                onClick={
                  isCurrentPlayer && onPlayCard
                    ? e => onPlayCard(i, card, e)
                    : undefined
                }
                disabled={!isCurrentPlayer || busy}
                dimmed={!isCurrentPlayer}
                hidden={hiddenHandIndex === i}
              />
            </TiltWrapper>
          )
        })}
      </div>
    </div>
  )
}

// ── TiltWrapper ──────────────────────────────────────────────────────────
// Isolated component so each card manages its own z-index ref without
// causing sibling re-renders when hover state changes.

interface TiltWrapperProps {
  rotate: number
  tiltY: number
  baseZIndex: number
  delay: number
  marginLeft: number
  children: React.ReactNode
}

function TiltWrapper({ rotate, tiltY, baseZIndex, delay, marginLeft, children }: TiltWrapperProps) {
  const ref = useRef<HTMLDivElement>(null)

  function onHoverStart() {
    if (ref.current) ref.current.style.zIndex = '100'
  }
  function onHoverEnd() {
    if (ref.current) ref.current.style.zIndex = String(baseZIndex)
  }

  return (
    <motion.div
      ref={ref}
      initial={{ y: tiltY - 50, opacity: 0, scale: 0.65, rotate }}
      animate={{ y: tiltY, opacity: 1, scale: 1, rotate }}
      onHoverStart={onHoverStart}
      onHoverEnd={onHoverEnd}
      transition={{
        type: 'spring',
        stiffness: 260,
        damping: 28,   // well-damped: no overshoot bounce
        delay,
      }}
      style={{
        display: 'flex',
        flexShrink: 0,
        marginLeft,
        zIndex: baseZIndex,
        position: 'relative',
        transformOrigin: 'bottom center',
      }}
    >
      {children}
    </motion.div>
  )
}
