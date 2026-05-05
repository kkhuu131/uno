import { motion } from 'framer-motion'
import type { CardColor } from '../types'

interface Props {
  onSelect: (color: CardColor) => void
  onCancel: () => void
}

const COLORS: { value: CardColor; label: string }[] = [
  { value: 'RED',    label: 'Red'    },
  { value: 'YELLOW', label: 'Yellow' },
  { value: 'GREEN',  label: 'Green'  },
  { value: 'BLUE',   label: 'Blue'   },
]

export function WildColorPicker({ onSelect, onCancel }: Props) {
  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <motion.div
        className="color-picker"
        onClick={e => e.stopPropagation()}
        initial={{ scale: 0.85, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        exit={{ scale: 0.85, opacity: 0 }}
        transition={{ type: 'spring', stiffness: 400, damping: 26 }}
      >
        <p className="color-picker__title">Choose a color ✨</p>

        <div className="color-picker__grid">
          {COLORS.map((c, i) => (
            <motion.button
              key={c.value}
              className={`color-circle color-circle--${c.value}`}
              onClick={() => onSelect(c.value)}
              aria-label={c.label}
              initial={{ scale: 0, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ type: 'spring', stiffness: 400, damping: 22, delay: i * 0.05 }}
              whileHover={{ scale: 1.15 }}
              whileTap={{ scale: 0.9 }}
            />
          ))}
        </div>

        <button className="color-picker__cancel" onClick={onCancel}>
          cancel
        </button>
      </motion.div>
    </div>
  )
}
