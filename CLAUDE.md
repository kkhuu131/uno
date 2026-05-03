# UNO — notes for agents

## API routes (`/api`)

- **POST `/games`** — Create game; returns `gameId`.
- **GET `/games/{gameId}`** — Snapshot: `players` (hands as `CardView` lists; `handIndex` matches array index), `topDiscard`, `activeColor`, `currentPlayerIndex`, `status` (`IN_PROGRESS` | `FINISHED`), `winnerPlayerIndex` / `winnerName` when done.
- **POST `/games/{gameId}/play`** — Body: `playerIndex`, `handIndex`, optional `chosenColor` for wilds. **409** if game already finished. **400** for rule/validation errors (`ErrorResponse`).
- **POST `/games/{gameId}/draw`** — Body: `playerIndex`, optional `endTurn` (default false). If `endTurn` is true, advances turn after drawing (not allowed while `pendingDrawStack` is true). Returns `drawnCard` + `game` snapshot.
- **POST `/games/{gameId}/pass`** — Body: `playerIndex`. Resolves a pending +2/+4 stack (current player takes cards) or passes the turn when no stack. **409** when finished.
- Snapshots include **`pendingDrawStack`**: when true, use **pass** or stack a +2/+4; deck **draw** is rejected by the domain until resolved.

## Known gotchas

- **Spring Boot 4:** `@WebMvcTest` lives in `org.springframework.boot.webmvc.test.autoconfigure`, not `...web.servlet`. Use `@MockitoBean` for mocks in slice tests.
- **Slice tests** that load `WebConfig` need `@Import` of `GameSnapshotMapper` and `ApiExceptionHandler` where relevant, plus `@EnableConfigurationProperties(CorsProperties.class)`.
- **`TestGames.register`** (tests only) reflects into `GameSessionService`’s map so we don’t expose a production hook for injecting games.
- **CORS** is configured for `/api/**` via `app.cors.allowed-origins` in `application.yaml` (defaults include Vite/React dev ports).
- **`GameState.playCardInternal`** calls `endTurn()` after `card.applyEffect(this)` (guarded by `!hasWinner()`). `NumberCard` and `WildCard` applyEffect do nothing to the turn; Skip/DrawTwo/Reverse call their own turn-advance logic inside applyEffect — so `endTurn()` correctly completes what those effects started.

## Frontend

Vite + React 18 + TypeScript in `frontend/`. Run with `npm run dev` (default port 5173; set `VITE_API_BASE` env var if the backend runs elsewhere). Card assets live in `frontend/uno_assets/` — naming convention: `{Color}_{0-9|Draw|Reverse|Skip}.png`, `Wild.png`, `Wild_Draw.png`, `Deck.png`.

- **Hand overflow**: `.hand-scroll` (outer, no overflow set) + `.hand` (inner, `overflow: visible`). CSS forbids `overflow-x: auto; overflow-y: visible` — setting either axis to auto silently forces the other to auto too, clipping hover-lifted and tilted cards.
- **Card z-index on hover**: Managed via DOM ref mutation in `TiltWrapper` (`onHoverStart`/`onHoverEnd`) rather than React state. React state would trigger sibling re-renders mid-animation and cause a visible flicker on hover exit.
