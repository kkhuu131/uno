import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useGame } from '../hooks/useGame'
import { clearLobbyCode, getLobbyCode } from '../utils/session'
import type { CardColor, CardView, GameSnapshot } from '../types'
import { getDeckImageSrc } from '../utils/cardImage'
import { ActionBar } from './ActionBar'
import type { DiscardEntry } from './DiscardPile'
import { DiscardPile } from './DiscardPile'
import type { FlyingCardData } from './FlyingCard'
import { FlyingCardOverlay } from './FlyingCard'
import { GameOver } from './GameOver'
import { OpponentSlot } from './OpponentSlot'
import { PlayerHand } from './PlayerHand'
import { Toast } from './Toast'
import { TurnBanner } from './TurnBanner'
import { WildColorPicker } from './WildColorPicker'

interface PendingWild {
  playerIndex: number
  handIndex: number
  card: CardView
}

function cardKey(card: CardView): string {
  return `${card.kind}-${card.color}-${card.number}-${card.action}-${card.wildType}`
}

// Returns absolute CSS position (left%, top%) for each player slot on an elliptical table.
// Slot 0 = local player (anchored at bottom-center). Others spread clockwise.
function slotPosition(slotIndex: number, totalPlayers: number): { left: string; top: string } {
  const angleDeg = (270 + (slotIndex * 360) / totalPlayers) % 360
  const rad = (angleDeg * Math.PI) / 180
  const cx = 50   // % center x
  const cy = 50   // % center y
  const rx = 40   // % horizontal radius
  const ry = 36   // % vertical radius
  const left = cx + rx * Math.cos(rad)
  const top = cy - ry * Math.sin(rad)
  return { left: `${left}%`, top: `${top}%` }
}

const CARD_W = 82
const CARD_H = 116

function isCardPlayable(
  card: CardView,
  topDiscard: GameSnapshot['topDiscard'],
  activeColor: CardColor,
  pendingDrawStack: boolean,
): boolean {
  if (pendingDrawStack) {
    if (card.kind === 'ACTION' && card.action === 'DRAW_TWO') return true
    if (card.kind === 'WILD' && card.wildType === 'WILD_DRAW_FOUR') return true
    return false
  }
  if (card.kind === 'WILD') return true
  if (card.color === activeColor) return true
  if (card.kind === 'NUMBER' && card.number !== null && card.number === topDiscard?.number) return true
  if (card.kind === 'ACTION' && card.action !== null && card.action === topDiscard?.action) return true
  return false
}

function delay(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

interface Props {
  gameId: string
  localPlayerIndex: number
}

export function GameTable({ gameId, localPlayerIndex }: Props) {
  const navigate = useNavigate()
  const lobbyCode = getLobbyCode()

  async function handlePlayAgain() {
    if (!lobbyCode) return
    await api.resetLobby(lobbyCode)
    navigate('/lobby/' + lobbyCode)
  }

  async function handleLeaveLobby() {
    if (!lobbyCode) return
    await api.leaveLobby(lobbyCode)
    clearLobbyCode()
    navigate('/')
  }

  const { snapshot, error, busy, clearError, playCard, passTurn, autoDrawLoopRef } = useGame(
    gameId,
    localPlayerIndex,
  )

  const [flyingCards, setFlyingCards] = useState<FlyingCardData[]>([])
  const [hiddenCard, setHiddenCard] = useState<{ playerIndex: number; handIndex: number } | null>(
    null,
  )
  const [bannerVisible, setBannerVisible] = useState(false)
  const [bannerName, setBannerName] = useState('')
  const prevPlayerRef = useRef<number | null>(null)
  const bannerTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [pendingWild, setPendingWild] = useState<PendingWild | null>(null)
  const [hasDrawnThisTurn, setHasDrawnThisTurn] = useState(false)
  const [discardHistory, setDiscardHistory] = useState<DiscardEntry[]>([])
  const [deckShuffling, setDeckShuffling] = useState(false)
  const discardCounterRef = useRef(0)
  const prevTopKeyRef = useRef<string | null>(null)

  const deckRef = useRef<HTMLButtonElement>(null)
  const discardRef = useRef<HTMLDivElement>(null)
  const localHandRef = useRef<HTMLDivElement | null>(null)

  const curIdx = snapshot?.currentPlayerIndex
  useEffect(() => { setHasDrawnThisTurn(false) }, [curIdx])

  useEffect(() => {
    if (snapshot == null) return
    if (
      prevPlayerRef.current !== null &&
      prevPlayerRef.current !== snapshot.currentPlayerIndex &&
      snapshot.status === 'IN_PROGRESS'
    ) {
      const name = snapshot.players[snapshot.currentPlayerIndex].name
      setBannerName(name)
      setBannerVisible(true)
      if (bannerTimerRef.current) clearTimeout(bannerTimerRef.current)
      bannerTimerRef.current = setTimeout(() => setBannerVisible(false), 1700)
    }
    prevPlayerRef.current = snapshot.currentPlayerIndex
  }, [snapshot?.currentPlayerIndex]) // eslint-disable-line

  useEffect(() => {
    if (!snapshot?.topDiscard) return
    const key = cardKey(snapshot.topDiscard)
    if (key !== prevTopKeyRef.current) {
      prevTopKeyRef.current = key
      const id = ++discardCounterRef.current
      setDiscardHistory((prev) => [{ id, card: snapshot.topDiscard }, ...prev].slice(0, 6))
    }
  }, [snapshot?.topDiscard]) // eslint-disable-line

  if (!snapshot) return <div className="table-loading">Connecting…</div>

  const { players, topDiscard, activeColor, pendingDrawStack, status, winnerName } = snapshot
  const currentIdx = snapshot.currentPlayerIndex
  const isMyTurn = currentIdx === localPlayerIndex
  const localPlayer = players[localPlayerIndex]

  const hasNoPlayableCards =
    isMyTurn &&
    !pendingDrawStack &&
    !hasDrawnThisTurn &&
    (localPlayer.hand ?? []).every(
      card => !isCardPlayable(card, topDiscard, activeColor, pendingDrawStack),
    )

  const playableFlags: boolean[] = (localPlayer.hand ?? []).map(card =>
    (isMyTurn && !busy && status !== 'FINISHED')
      ? isCardPlayable(card, topDiscard, activeColor, pendingDrawStack)
      : false,
  )

  // Build render order: local player at slot 0, others clockwise
  const totalPlayers = players.length
  const renderOrder = Array.from({ length: totalPlayers }, (_, slot) =>
    (localPlayerIndex + slot) % totalPlayers,
  )

  function addFlyingCard(fc: FlyingCardData) {
    setFlyingCards((prev) => [...prev, fc])
  }
  function removeFlyingCard(id: string) {
    setFlyingCards((prev) => prev.filter((f) => f.id !== id))
  }

  function handleCardClick(
    playerIndex: number,
    handIndex: number,
    card: CardView,
    e: React.MouseEvent<HTMLButtonElement>,
  ) {
    if (!isCardPlayable(card, topDiscard, activeColor, pendingDrawStack)) return
    if (card.kind === 'WILD') {
      setPendingWild({ playerIndex, handIndex, card })
      return
    }
    startPlayAnimation(card, e.currentTarget.getBoundingClientRect())
    void playCard(playerIndex, handIndex)
  }

  function handleColorPick(color: CardColor) {
    if (!pendingWild) return
    const { playerIndex, handIndex, card } = pendingWild
    setPendingWild(null)
    const discardRect = discardRef.current?.getBoundingClientRect()
    const handRect = localHandRef.current?.getBoundingClientRect()
    if (discardRect && handRect) {
      const id = `play-${Date.now()}`
      setHiddenCard({ playerIndex, handIndex })
      addFlyingCard({
        id,
        card,
        from: { x: handRect.left + 20, y: handRect.top + 10, w: CARD_W, h: CARD_H },
        to: { x: discardRect.left, y: discardRect.top, w: discardRect.width, h: discardRect.height },
        onComplete: () => { removeFlyingCard(id); setHiddenCard(null) },
      })
    }
    void playCard(playerIndex, handIndex, color)
  }

  function startPlayAnimation(card: CardView, cardRect: DOMRect) {
    const discardRect = discardRef.current?.getBoundingClientRect()
    if (!discardRect) return
    const id = `play-${Date.now()}`
    setHiddenCard({ playerIndex: localPlayerIndex, handIndex: -1 })
    addFlyingCard({
      id,
      card,
      from: { x: cardRect.left, y: cardRect.top, w: cardRect.width, h: cardRect.height },
      to: { x: discardRect.left, y: discardRect.top, w: discardRect.width, h: discardRect.height },
      onComplete: () => { removeFlyingCard(id); setHiddenCard(null) },
    })
  }

  async function animateDrawFromDeck(): Promise<void> {
    return new Promise(resolve => {
      const deckRect = deckRef.current?.getBoundingClientRect()
      const handRect = localHandRef.current?.getBoundingClientRect()
      if (!deckRect || !handRect) { resolve(); return }
      const id = `draw-${Date.now()}`
      const toX = Math.min(handRect.right - CARD_W - 8, handRect.left + handRect.width * 0.7)
      const toY = handRect.top + (handRect.height - CARD_H) / 2
      addFlyingCard({
        id,
        card: null,
        from: { x: deckRect.left, y: deckRect.top, w: deckRect.width, h: deckRect.height },
        to: { x: toX, y: toY, w: CARD_W, h: CARD_H },
        onComplete: () => { removeFlyingCard(id); resolve() },
      })
    })
  }

  async function handleDraw() {
    await autoDrawLoopRef.current(async (_card, reshuffled, stillDrawing) => {
      if (reshuffled) {
        setDeckShuffling(true)
        await delay(750)
        setDeckShuffling(false)
        await delay(150)
      }
      await animateDrawFromDeck()
      if (stillDrawing) await delay(180)
    })
    setHasDrawnThisTurn(true)
  }

  async function handlePass() {
    await passTurn(localPlayerIndex)
    setHasDrawnThisTurn(false)
  }

  return (
    <div className="table">
      <TurnBanner visible={bannerVisible} name={bannerName} isMyTurn={isMyTurn} />

      {/* ── Circular opponent slots ── */}
      {renderOrder.slice(1).map((playerIdx, i) => {
        const slot = i + 1
        const pos = slotPosition(slot, totalPlayers)
        return (
          <OpponentSlot
            key={playerIdx}
            player={players[playerIdx]}
            isActive={currentIdx === playerIdx}
            style={{
              position: 'absolute',
              left: pos.left,
              top: pos.top,
              transform: 'translate(-50%, -50%)',
            }}
          />
        )
      })}

      {/* ── Table center ── */}
      <div className="table-center">
        <div className="table-center__inner">
          <button
            ref={deckRef}
            className={`deck-pile${deckShuffling ? ' deck-pile--shuffling' : ''}`}
            onClick={isMyTurn ? handleDraw : undefined}
            disabled={!isMyTurn || busy || pendingDrawStack || hasDrawnThisTurn || status === 'FINISHED'}
            aria-label="Draw card from deck"
          >
            <img src={getDeckImageSrc()} alt="Draw pile" draggable={false} />
          </button>

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
            : currentIdx === localPlayerIndex
            ? 'Your turn'
            : `${players[currentIdx].name}'s turn`}
        </div>
      </div>

      {/* ── Local player (bottom) ── */}
      <div className={`player-zone player-zone--local${isMyTurn ? ' player-zone--active' : ''}`}>
        {isMyTurn && (
          <ActionBar
            playerIndex={localPlayerIndex}
            pendingDrawStack={pendingDrawStack}
            hasDrawnThisTurn={hasDrawnThisTurn}
            noPlayableCards={hasNoPlayableCards}
            busy={busy}
            onDraw={handleDraw}
            onPass={handlePass}
          />
        )}

        <PlayerHand
          hand={localPlayer.hand ?? []}
          playerIndex={localPlayerIndex}
          isCurrentPlayer={isMyTurn}
          onPlayCard={(hi, card, e) => handleCardClick(localPlayerIndex, hi, card, e)}
          busy={busy}
          hiddenHandIndex={
            hiddenCard?.playerIndex === localPlayerIndex ? hiddenCard.handIndex : null
          }
          handRef={localHandRef}
          playableFlags={playableFlags}
        />

        <div className="player-info">
          {isMyTurn && <span className="player-info__turn-pip" />}
          <span className="player-info__name">{localPlayer.name}</span>
          <span className="player-info__count">{(localPlayer.hand ?? []).length} cards</span>
        </div>
      </div>

      <FlyingCardOverlay cards={flyingCards} />

      {pendingWild && (
        <WildColorPicker onSelect={handleColorPick} onCancel={() => setPendingWild(null)} />
      )}

      {status === 'FINISHED' && winnerName && (
        <GameOver
          winnerName={winnerName}
          onNewGame={() => navigate('/')}
          onPlayAgain={lobbyCode ? handlePlayAgain : undefined}
          onLeaveLobby={lobbyCode ? handleLeaveLobby : undefined}
        />
      )}

      {error && <Toast message={error} onDismiss={clearError} />}
    </div>
  )
}
