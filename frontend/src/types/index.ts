export type CardKind = 'NUMBER' | 'ACTION' | 'WILD'
export type CardColor = 'RED' | 'GREEN' | 'BLUE' | 'YELLOW'
export type ActionType = 'SKIP' | 'REVERSE' | 'DRAW_TWO'
export type WildType = 'WILD' | 'WILD_DRAW_FOUR'
export type GameStatus = 'IN_PROGRESS' | 'FINISHED'

export interface CardView {
  kind: CardKind
  color: CardColor | null
  number: number | null
  action: ActionType | null
  wildType: WildType | null
}

export interface PlayerStateView {
  name: string
  hand: CardView[]
}

export interface GameSnapshot {
  gameId: string
  currentPlayerIndex: number
  activeColor: CardColor
  players: PlayerStateView[]
  topDiscard: CardView
  status: GameStatus
  winnerPlayerIndex: number | null
  winnerName: string | null
  pendingDrawStack: boolean
}

export interface DrawCardResponse {
  drawnCard: CardView
  game: GameSnapshot
}
