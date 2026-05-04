import { useEffect, useRef, useState } from 'react'
import { getDisplayName, setDisplayName } from '../utils/session'

interface Props {
  onChange?: (name: string) => void
}

export function UsernameBar({ onChange }: Props) {
  const [name, setName] = useState(getDisplayName)
  const [editing, setEditing] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editing) inputRef.current?.select()
  }, [editing])

  function commit() {
    const trimmed = name.trim()
    if (trimmed) {
      setDisplayName(trimmed)
      setName(trimmed)
      onChange?.(trimmed)
    } else {
      setName(getDisplayName())
    }
    setEditing(false)
  }

  return (
    <div className="username-bar">
      {editing ? (
        <input
          ref={inputRef}
          className="username-bar__input"
          value={name}
          maxLength={24}
          onChange={(e) => setName(e.target.value)}
          onBlur={commit}
          onKeyDown={(e) => {
            if (e.key === 'Enter') commit()
            if (e.key === 'Escape') { setName(getDisplayName()); setEditing(false) }
          }}
        />
      ) : (
        <span className="username-bar__display">
          {name || <span className="username-bar__placeholder">Set your name</span>}
          <button className="username-bar__edit" onClick={() => setEditing(true)} aria-label="Edit name">
            ✎
          </button>
        </span>
      )}
    </div>
  )
}
