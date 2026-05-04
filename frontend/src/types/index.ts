export type CardKind = 'NUMBER' | 'ACTION' | 'WILD'
export type CardColor = 'RED' | 'GREEN' | 'BLUE' | 'YELLOW'
export type ActionType = 'SKIP' | 'REVERSE' | 'DRAW_TWO'
export type WildType = 'WILD' | 'WILD_DRAW_FOUR'
export type GameStatus = 'IN_PROGRESS' | 'FINISHED'
export type LobbyStatus = 'WAITING' | 'IN_PROGRESS'

export interface CardView {
  kind: CardKind
  color: CardColor | null
  number: number | null
  action: ActionType | null
  wildType: WildType | null
}

export interface PlayerStateView {
  name: string
  hand: CardView[] | null  // null = redacted (opponent); populated = local player
  handSize: number
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
  mustDrawAgain: boolean
  deckReshuffled: boolean
}

export interface PrivateHandUpdate {
  gameId: string
  playerIndex: number
  hand: CardView[]
}

export interface LobbyPlayerView {
  playerIndex: number
  displayName: string
}

export interface LobbySnapshot {
  code: string
  hostPlayerIndex: number
  players: LobbyPlayerView[]
  status: LobbyStatus
  gameId: string | null
}
