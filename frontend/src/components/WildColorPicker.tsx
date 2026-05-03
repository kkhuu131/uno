import { motion } from 'framer-motion'
import type { CardColor } from '../types'

interface Props {
  onSelect: (color: CardColor) => void
  onCancel: () => void
}

const COLORS: { value: CardColor; label: string; emoji: string }[] = [
  { value: 'RED',    label: 'Red',    emoji: '🔴' },
  { value: 'YELLOW', label: 'Yellow', emoji: '🟡' },
  { value: 'GREEN',  label: 'Green',  emoji: '🟢' },
  { value: 'BLUE',   label: 'Blue',   emoji: '🔵' },
]

export function WildColorPicker({ onSelect, onCancel }: Props) {
  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <motion.div
        className="color-picker"
        onClick={e => e.stopPropagation()}
        initial={{ scale: 0.7, opacity: 0, y: 20 }}
        animate={{ scale: 1, opacity: 1, y: 0 }}
        exit={{ scale: 0.7, opacity: 0, y: 20 }}
        transition={{ type: 'spring', stiffness: 380, damping: 26 }}
      >
        <p className="color-picker__title">Choose a color ✨</p>

        <div className="color-picker__grid">
          {COLORS.map((c, i) => (
            <motion.button
              key={c.value}
              className={`color-btn color-btn--${c.value}`}
              onClick={() => onSelect(c.value)}
              aria-label={c.label}
              initial={{ scale: 0, rotate: -20 }}
              animate={{ scale: 1, rotate: 0 }}
              transition={{ type: 'spring', stiffness: 400, damping: 22, delay: i * 0.06 }}
              whileHover={{ scale: 1.1, rotate: 3 }}
              whileTap={{ scale: 0.93 }}
            >
              {c.emoji} {c.label}
            </motion.button>
          ))}
        </div>

        <button className="color-picker__cancel" onClick={onCancel}>
          cancel
        </button>
      </motion.div>
    </div>
  )
}
