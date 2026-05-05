# Playable Card Glow — Design Spec

**Date:** 2026-05-04

## Summary

Add an animated gold pulsing glow to cards in the local player's hand that are legally playable on their current turn. This includes stacking scenarios: when a `+2`/`+4` pending draw stack is active, only `+2` (on a `+2`) or `+4` (on either) cards glow, matching the existing `isCardPlayable` logic.

---

## Architecture

### Approach

Option A: compute playability in `GameTable` (where `isCardPlayable` and all required state already live), pass a boolean array down through `PlayerHand` to `Card`. Purely a frontend concern — the backend is unaffected.

---

## Component Changes

### `GameTable.tsx`

- Always compute `playableFlags: boolean[]` by mapping over `localPlayer.hand` and calling `isCardPlayable(card, topDiscard, activeColor, pendingDrawStack)` for each card.
- When `isMyTurn` is false, or `busy` is true, all flags are `false` (no glow while an animation is in flight).
- Pass `playableFlags` as a new prop to the `<PlayerHand>` render.

### `PlayerHand.tsx`

- Add `playableFlags?: boolean[]` to the `Props` interface.
- In the card map, read `playableFlags?.[i] ?? false` and pass it as `isPlayable` to each `<Card>`.

### `Card.tsx`

- Add `isPlayable?: boolean` to the `Props` interface.
- When `isPlayable` is true, add the CSS class `card--playable` to the button element.

### `index.css`

- Add a `@keyframes pulse-gold` animation that cycles the `box-shadow` between a dim and bright gold glow:
  - Low: `0 0 8px 2px rgba(255, 200, 50, 0.4)`
  - High: `0 0 18px 6px rgba(255, 200, 50, 0.85)`
- `.card--playable` applies `animation: pulse-gold 1.4s ease-in-out infinite`.
- The glow is on the card element itself — border-radius is inherited, no interference with `TiltWrapper` z-index or hover lift.

---

## Behaviour Notes

- The glow is computed fresh on every snapshot update, so it always reflects the current game state (color change from a wild, stack resolution, etc.).
- Non-playable cards during the player's turn receive no special treatment — they just sit inert as usual.
- The glow does not appear when it is not the local player's turn.

---

## Out of Scope

- Hand reordering / drag-to-sort (tracked separately for future design).
- Any backend changes.
