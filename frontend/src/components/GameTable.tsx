import { useEffect, useRef, useState } from 'react'
import { useGame } from '../hooks/useGame'
import type { CardColor, CardView } from '../types'
import { getDeckImageSrc } from '../utils/cardImage'
import { ActionBar } from './ActionBar'
import type { DiscardEntry } from './DiscardPile'
import { DiscardPile } from './DiscardPile'
import type { FlyingCardData } from './FlyingCard'
import { FlyingCardOverlay } from './FlyingCard'
import { GameOver } from './GameOver'
import { PlayerHand } from './PlayerHand'
import { Toast } from './Toast'
import { TurnBanner } from './TurnBanner'
import { WildColorPicker } from './WildColorPicker'

interface Props {
  gameId: string
  onNewGame: () => void
}

interface PendingWild {
  playerIndex: number
  handIndex: number
  card: CardView
}

function cardKey(card: CardView): string {
  return `${card.kind}-${card.color}-${card.number}-${card.action}-${card.wildType}`
}

const CARD_W = 82
const CARD_H = 116

export function GameTable({ gameId, onNewGame }: Props) {
  const { snapshot, error, busy, clearError, playCard, drawCard, passTurn } = useGame(gameId)

  // Animation state
  const [flyingCards, setFlyingCards] = useState<FlyingCardData[]>([])
  const [hiddenCard, setHiddenCard] = useState<{ playerIndex: number; handIndex: number } | null>(null)

  // Turn banner state
  const [bannerVisible, setBannerVisible] = useState(false)
  const [bannerName, setBannerName] = useState('')
  const prevPlayerRef = useRef<number | null>(null)
  const bannerTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Game interaction state
  const [pendingWild, setPendingWild] = useState<PendingWild | null>(null)
  const [hasDrawnThisTurn, setHasDrawnThisTurn] = useState(false)

  // Discard pile history (tracked locally — API only exposes topDiscard)
  const [discardHistory, setDiscardHistory] = useState<DiscardEntry[]>([])
  const discardCounterRef = useRef(0)
  const prevTopKeyRef = useRef<string | null>(null)

  // DOM refs for animation positions
  const deckRef    = useRef<HTMLButtonElement>(null)
  const discardRef = useRef<HTMLDivElement>(null)
  const p0HandRef  = useRef<HTMLDivElement | null>(null)
  const p1HandRef  = useRef<HTMLDivElement | null>(null)

  // Reset draw flag when turn changes
  const curIdx = snapshot?.currentPlayerIndex
  useEffect(() => {
    setHasDrawnThisTurn(false)
  }, [curIdx])

  // Show turn banner on player change
  useEffect(() => {
    if (snapshot == null) return
    if (prevPlayerRef.current !== null && prevPlayerRef.current !== snapshot.currentPlayerIndex && snapshot.status === 'IN_PROGRESS') {
      const name = snapshot.players[snapshot.currentPlayerIndex].name
      setBannerName(name)
      setBannerVisible(true)
      if (bannerTimerRef.current) clearTimeout(bannerTimerRef.current)
      bannerTimerRef.current = setTimeout(() => setBannerVisible(false), 1700)
    }
    prevPlayerRef.current = snapshot.currentPlayerIndex
  }, [snapshot?.currentPlayerIndex]) // eslint-disable-line react-hooks/exhaustive-deps

  // Build local discard pile history — append whenever topDiscard changes
  useEffect(() => {
    if (!snapshot?.topDiscard) return
    const key = cardKey(snapshot.topDiscard)
    if (key !== prevTopKeyRef.current) {
      prevTopKeyRef.current = key
      const id = ++discardCounterRef.current
      setDiscardHistory(prev => [{ id, card: snapshot.topDiscard }, ...prev].slice(0, 6))
    }
  }, [snapshot?.topDiscard]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!snapshot) {
    return <div className="table-loading">Shuffling deck…</div>
  }

  const { players, topDiscard, activeColor, pendingDrawStack, status, winnerName } = snapshot
  const player0 = players[0]
  const player1 = players[1]
  const currentIdx = snapshot.currentPlayerIndex

  // ── Helpers ─────────────────────────────────────────────────────────

  function addFlyingCard(fc: FlyingCardData) {
    setFlyingCards(prev => [...prev, fc])
  }

  function removeFlyingCard(id: string) {
    setFlyingCards(prev => prev.filter(f => f.id !== id))
  }

  function getHandRef(playerIndex: number) {
    return playerIndex === 0 ? p0HandRef : p1HandRef
  }

  // ── Card play ────────────────────────────────────────────────────────

  function handleCardClick(playerIndex: number, handIndex: number, card: CardView, e: React.MouseEvent<HTMLButtonElement>) {
    if (card.kind === 'WILD') {
      setPendingWild({ playerIndex, handIndex, card })
      return
    }
    startPlayAnimation(playerIndex, handIndex, card, e.currentTarget.getBoundingClientRect())
    void playCard(playerIndex, handIndex)
  }

  function handleColorPick(color: CardColor) {
    if (!pendingWild) return
    const { playerIndex, handIndex, card } = pendingWild
    setPendingWild(null)

    // For wild, get card position from discard (approximation; wild was in hand but no event ref)
    const discardRect = discardRef.current?.getBoundingClientRect()
    const handRef = getHandRef(playerIndex)
    const handRect = handRef.current?.getBoundingClientRect()

    if (discardRect && handRect) {
      const id = `play-${Date.now()}`
      setHiddenCard({ playerIndex, handIndex })
      addFlyingCard({
        id, card,
        from: { x: handRect.left + 20, y: handRect.top + 10, w: CARD_W, h: CARD_H },
        to:   { x: discardRect.left, y: discardRect.top, w: discardRect.width, h: discardRect.height },
        onComplete: () => { removeFlyingCard(id); setHiddenCard(null) },
      })
    }

    void playCard(playerIndex, handIndex, color)
  }

  function startPlayAnimation(playerIndex: number, handIndex: number, card: CardView, cardRect: DOMRect) {
    const discardRect = discardRef.current?.getBoundingClientRect()
    if (!discardRect) return

    const id = `play-${Date.now()}`
    setHiddenCard({ playerIndex, handIndex })
    addFlyingCard({
      id, card,
      from: { x: cardRect.left, y: cardRect.top, w: cardRect.width, h: cardRect.height },
      to:   { x: discardRect.left, y: discardRect.top, w: discardRect.width, h: discardRect.height },
      onComplete: () => { removeFlyingCard(id); setHiddenCard(null) },
    })
  }

  // ── Card draw ────────────────────────────────────────────────────────

  async function handleDraw() {
    const deckRect   = deckRef.current?.getBoundingClientRect()
    const handRef    = getHandRef(currentIdx)
    const handRect   = handRef.current?.getBoundingClientRect()

    if (deckRect && handRect) {
      const id = `draw-${Date.now()}`
      const toX = Math.min(handRect.right - CARD_W - 8, handRect.left + handRect.width * 0.7)
      const toY = handRect.top + (handRect.height - CARD_H) / 2
      addFlyingCard({
        id,
        card: null, // deck back
        from: { x: deckRect.left, y: deckRect.top, w: deckRect.width, h: deckRect.height },
        to:   { x: toX, y: toY, w: CARD_W, h: CARD_H },
        onComplete: () => removeFlyingCard(id),
      })
    }

    await drawCard(currentIdx, false)
    setHasDrawnThisTurn(true)
  }

  // ── Pass / End turn ──────────────────────────────────────────────────

  async function handlePass() {
    await passTurn(currentIdx)
    setHasDrawnThisTurn(false)
  }

  // ── Render ───────────────────────────────────────────────────────────

  const p1Active = currentIdx === 1
  const p0Active = currentIdx === 0

  return (
    <div className="table">
      <TurnBanner visible={bannerVisible} name={bannerName} />

      {/* ── Player 1 (top) ── */}
      <div className={`player-zone player-zone--top player-zone--p1 ${p1Active ? 'player-zone--active' : ''}`}>
        <div className="player-info">
          {p1Active && <span className="player-info__turn-pip" />}
          <span className="player-info__avatar player-info__avatar--p1">P1</span>
          <span className="player-info__name">{player1.name}</span>
          <span className="player-info__count">{player1.hand.length} cards</span>
        </div>

        <PlayerHand
          hand={player1.hand}
          playerIndex={1}
          isCurrentPlayer={p1Active}
          flipped
          onPlayCard={(hi, card, e) => handleCardClick(1, hi, card, e)}
          busy={busy}
          hiddenHandIndex={hiddenCard?.playerIndex === 1 ? hiddenCard.handIndex : null}
          handRef={p1HandRef}
        />

        {p1Active && (
          <ActionBar
            playerIndex={1}
            pendingDrawStack={pendingDrawStack}
            hasDrawnThisTurn={hasDrawnThisTurn}
            busy={busy}
            onDraw={handleDraw}
            onPass={handlePass}
          />
        )}
      </div>

      {/* ── Table center ── */}
      <div className="table-center">
        <div className="table-center__inner">
          {/* Draw deck */}
          <button
            ref={deckRef}
            className="deck-pile"
            onClick={handleDraw}
            disabled={busy || pendingDrawStack || hasDrawnThisTurn || status === 'FINISHED'}
            aria-label="Draw card from deck"
            title={pendingDrawStack ? 'Resolve draw stack first' : 'Draw a card'}
          >
            <img src={getDeckImageSrc()} alt="Draw pile" draggable={false} />
          </button>

          {/* Discard pile — stacked pile with history */}
          <DiscardPile
            history={discardHistory}
            activeColor={activeColor}
            pileRef={discardRef}
          />

          {pendingDrawStack && <div className="stack-badge">STACK!</div>}
        </div>

        <div className="table-center__status">
          {status === 'FINISHED'
            ? 'Game over'
            : `${players[currentIdx].name}'s turn`}
        </div>
      </div>

      {/* ── Player 0 (bottom) ── */}
      <div className={`player-zone player-zone--p2 ${p0Active ? 'player-zone--active' : ''}`}>
        {p0Active && (
          <ActionBar
            playerIndex={0}
            pendingDrawStack={pendingDrawStack}
            hasDrawnThisTurn={hasDrawnThisTurn}
            busy={busy}
            onDraw={handleDraw}
            onPass={handlePass}
          />
        )}

        <PlayerHand
          hand={player0.hand}
          playerIndex={0}
          isCurrentPlayer={p0Active}
          onPlayCard={(hi, card, e) => handleCardClick(0, hi, card, e)}
          busy={busy}
          hiddenHandIndex={hiddenCard?.playerIndex === 0 ? hiddenCard.handIndex : null}
          handRef={p0HandRef}
        />

        <div className="player-info">
          {p0Active && <span className="player-info__turn-pip" />}
          <span className="player-info__avatar player-info__avatar--p2">P2</span>
          <span className="player-info__name">{player0.name}</span>
          <span className="player-info__count">{player0.hand.length} cards</span>
        </div>
      </div>

      {/* ── Overlays ── */}
      <FlyingCardOverlay cards={flyingCards} />

      {pendingWild && (
        <WildColorPicker
          onSelect={handleColorPick}
          onCancel={() => setPendingWild(null)}
        />
      )}

      {status === 'FINISHED' && winnerName && (
        <GameOver winnerName={winnerName} onNewGame={onNewGame} />
      )}

      {error && <Toast message={error} onDismiss={clearError} />}
    </div>
  )
}
