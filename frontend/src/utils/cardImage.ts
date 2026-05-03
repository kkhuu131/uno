import type { CardView } from '../types'

const raw = import.meta.glob('../../uno_assets/*.png', {
  eager: true,
}) as Record<string, { default: string }>

const images: Record<string, string> = {}
for (const [path, mod] of Object.entries(raw)) {
  const filename = path.split('/').pop()!.replace('.png', '')
  images[filename] = mod.default
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase()
}

export function getCardImageName(card: CardView): string {
  if (card.kind === 'WILD') {
    return card.wildType === 'WILD_DRAW_FOUR' ? 'Wild_Draw' : 'Wild'
  }
  const color = capitalize(card.color ?? 'Red')
  if (card.kind === 'NUMBER') {
    return `${color}_${card.number}`
  }
  // ACTION
  if (card.action === 'DRAW_TWO') return `${color}_Draw`
  if (card.action === 'SKIP') return `${color}_Skip`
  return `${color}_Reverse`
}

export function getCardImageSrc(card: CardView): string {
  return images[getCardImageName(card)] ?? ''
}

export function getDeckImageSrc(): string {
  return images['Deck'] ?? ''
}
