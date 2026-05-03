import { useEffect, useState } from 'react'

interface Piece {
  id: number
  left: number
  width: number
  height: number
  color: string
  delay: number
  duration: number
  isCircle: boolean
}

const COLORS = ['#EF3054', '#FFB800', '#00B96B', '#1A7FE8', '#FF4D8D', '#4D7EFF', '#FF8C00', '#C452FF']

function makePieces(count: number): Piece[] {
  return Array.from({ length: count }, (_, i) => ({
    id: i,
    left: Math.random() * 100,
    width: 6 + Math.random() * 10,
    height: 8 + Math.random() * 12,
    color: COLORS[Math.floor(Math.random() * COLORS.length)],
    delay: Math.random() * 2.5,
    duration: 2.2 + Math.random() * 2,
    isCircle: Math.random() > 0.6,
  }))
}

export function Confetti() {
  const [pieces] = useState(() => makePieces(70))

  return (
    <div className="confetti-container">
      {pieces.map(p => (
        <div
          key={p.id}
          className="confetti-piece"
          style={{
            left: `${p.left}%`,
            width: p.width,
            height: p.height,
            background: p.color,
            animationDelay: `${p.delay}s`,
            animationDuration: `${p.duration}s`,
            borderRadius: p.isCircle ? '50%' : '2px',
          }}
        />
      ))}
    </div>
  )
}

export function AutoConfetti({ active }: { active: boolean }) {
  const [show, setShow] = useState(false)
  useEffect(() => {
    if (active) {
      setShow(true)
      // Keep confetti alive as long as the overlay is shown
    } else {
      setShow(false)
    }
  }, [active])
  return show ? <Confetti /> : null
}
