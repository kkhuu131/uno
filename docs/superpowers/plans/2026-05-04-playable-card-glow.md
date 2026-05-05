# Playable Card Glow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Animate a pulsing gold glow on every card in the local player's hand that is legally playable on their current turn, including stacking scenarios.

**Architecture:** `GameTable` computes a `playableFlags: boolean[]` array using the existing `isCardPlayable` function and passes it to `PlayerHand`, which passes a per-card `isPlayable` boolean to each `Card`. `Card` applies a `card--playable` CSS class that drives a `@keyframes` pulse animation. All changes are purely frontend; the backend is unaffected.

**Tech Stack:** React 18, TypeScript, Framer Motion, CSS keyframes

> **No unit test infrastructure exists for frontend components in this repo.** Each task ends with a manual visual verification step instead of automated tests.

---

## File Map

| File | Change |
|------|--------|
| `frontend/src/index.css` | Add `@keyframes pulse-gold` and `.card--playable` rule |
| `frontend/src/components/Card.tsx` | Add `isPlayable?: boolean` prop; apply `card--playable` class |
| `frontend/src/components/PlayerHand.tsx` | Add `playableFlags?: boolean[]` prop; pass `isPlayable` to each `Card` |
| `frontend/src/components/GameTable.tsx` | Compute `playableFlags`; pass to `<PlayerHand>` |

---

## Task 1: Add CSS animation

**Files:**
- Modify: `frontend/src/index.css:215` (after `.card--dimmed`)

- [ ] **Step 1: Add keyframes and playable class**

In `frontend/src/index.css`, insert directly after the `.card--dimmed` rule (line 215):

```css
@keyframes pulse-gold {
  0%, 100% { box-shadow: var(--card-shadow), 0 0 8px 2px rgba(255, 200, 50, 0.4); }
  50%       { box-shadow: var(--card-shadow), 0 0 18px 6px rgba(255, 200, 50, 0.85); }
}

.card--playable { animation: pulse-gold 1.4s ease-in-out infinite; }
```

Note: we chain `var(--card-shadow)` first so the existing drop-shadow is preserved — we're adding the gold glow on top, not replacing it.

- [ ] **Step 2: Commit**

```bash
git add frontend/src/index.css
git commit -m "feat(frontend): add pulse-gold keyframe animation for playable cards"
```

---

## Task 2: Add `isPlayable` prop to `Card`

**Files:**
- Modify: `frontend/src/components/Card.tsx:5-13` (Props interface), `Card.tsx:23-31` (className array)

- [ ] **Step 1: Add `isPlayable` to the Props interface**

In `frontend/src/components/Card.tsx`, update the `Props` interface (lines 5–13):

```typescript
interface Props {
  card: CardView
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void
  disabled?: boolean
  dimmed?: boolean
  hidden?: boolean
  isPlayable?: boolean
  style?: React.CSSProperties
  className?: string
}
```

- [ ] **Step 2: Destructure `isPlayable` and apply CSS class**

Update the function signature and className array in `Card.tsx` (lines 15–31):

```typescript
export function Card({ card, onClick, disabled, dimmed, hidden, isPlayable, style, className }: Props) {
  const src = getCardImageSrc(card)
  const interactive = !!onClick && !disabled

  return (
    <motion.button
      className={[
        'card',
        interactive ? 'card--interactive' : '',
        dimmed ? 'card--dimmed' : '',
        hidden ? 'card--invisible' : '',
        isPlayable ? 'card--playable' : '',
        className ?? '',
      ]
        .filter(Boolean)
        .join(' ')}
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/Card.tsx
git commit -m "feat(frontend): add isPlayable prop to Card for glow class"
```

---

## Task 3: Thread `playableFlags` through `PlayerHand`

**Files:**
- Modify: `frontend/src/components/PlayerHand.tsx:6-15` (Props interface), `PlayerHand.tsx:30-38` (destructure), `PlayerHand.tsx:51-82` (card map)

- [ ] **Step 1: Add `playableFlags` to the Props interface**

In `frontend/src/components/PlayerHand.tsx`, update the `Props` interface (lines 6–15):

```typescript
interface Props {
  hand: CardView[]
  playerIndex: number
  isCurrentPlayer: boolean
  flipped?: boolean
  onPlayCard?: (handIndex: number, card: CardView, event: React.MouseEvent<HTMLButtonElement>) => void
  busy?: boolean
  hiddenHandIndex?: number | null
  handRef?: React.Ref<HTMLDivElement>
  playableFlags?: boolean[]
}
```

- [ ] **Step 2: Destructure `playableFlags` in the function signature**

Update the destructure (lines 30–38):

```typescript
export function PlayerHand({
  hand,
  isCurrentPlayer,
  flipped,
  onPlayCard,
  busy,
  hiddenHandIndex,
  handRef,
  playableFlags,
}: Props) {
```

- [ ] **Step 3: Pass `isPlayable` to each `Card` in the map**

Update the `<Card>` render inside the map (lines 69–79) — add `isPlayable`:

```typescript
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
                isPlayable={playableFlags?.[i] ?? false}
              />
```

- [ ] **Step 4: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/PlayerHand.tsx
git commit -m "feat(frontend): thread playableFlags through PlayerHand to Card"
```

---

## Task 4: Compute `playableFlags` in `GameTable` and pass to `PlayerHand`

**Files:**
- Modify: `frontend/src/components/GameTable.tsx:134-140` (after `hasNoPlayableCards`), `GameTable.tsx:309-319` (`<PlayerHand>` render)

- [ ] **Step 1: Compute `playableFlags` after `hasNoPlayableCards`**

In `frontend/src/components/GameTable.tsx`, insert directly after the `hasNoPlayableCards` block (after line 140):

```typescript
  const playableFlags: boolean[] = (localPlayer.hand ?? []).map(card =>
    (isMyTurn && !busy)
      ? isCardPlayable(card, topDiscard, activeColor, pendingDrawStack)
      : false,
  )
```

`busy` suppresses the glow during card-play animations so the glowing cards don't distract mid-flight.

- [ ] **Step 2: Pass `playableFlags` to `<PlayerHand>`**

Update the `<PlayerHand>` render (lines 309–319) to add the new prop:

```typescript
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
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 4: Start the dev server and manually verify**

```bash
cd frontend && npm run dev
```

Open `http://localhost:5173` in a browser. Start or join a game. Verify:

1. **Your turn, normal state:** Cards that match the top discard color, number, or action type glow with a pulsing gold animation. Wild cards always glow.
2. **Your turn, pending +2/+4 stack:** Only `Draw Two` and `Wild Draw Four` cards glow; all other cards do not.
3. **Not your turn:** No cards glow.
4. **During a card-play animation (`busy`):** Glow is suppressed.
5. **Glow does not appear on opponent hands** (they use `OpponentSlot`, not `PlayerHand` with `playableFlags`).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/GameTable.tsx
git commit -m "feat(frontend): highlight playable cards with animated gold glow on player's turn"
```
