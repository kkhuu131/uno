# Multiplayer Lobby Design

**Date:** 2026-05-03  
**Status:** Approved  

---

## Goal

Replace the current single-device hot-seat mode with real multiplayer: players join from separate devices, see only their own cards, and interact in real time. Supports 2–10 players per game.

---

## Architecture Overview

Two communication layers:

- **REST** — mutations (create lobby, join, start game, play/draw/pass card)
- **STOMP/WebSocket** — push notifications (lobby updates, game state broadcast)

```
Client (browser)
  localStorage: sessionId (UUID), displayName
  │
  ├─ REST  ──────────────────────────────►  /api/lobbies          (create, join, start)
  │                                         /api/games/{id}/play|draw|pass
  │
  └─ STOMP/WebSocket ◄──────────────────── /topic/lobbies/{code}
                                            /topic/games/{gameId}
                                            /topic/games/{gameId}/private/{sessionId}
```

### Identity Model

- On first visit, the browser generates a UUID `sessionId` stored in `localStorage` under `uno_session_id`.
- `displayName` is stored in `localStorage` under `uno_display_name`, editable at any time.
- No server accounts or passwords. The `sessionId` is passed as a `X-Session-Id` HTTP header on all REST mutations and in the STOMP CONNECT frame.

### Card Privacy Model

- The public topic `/topic/games/{gameId}` broadcasts a `PublicGameSnapshot` — full game state but opponent hands contain only `handSize` (no card details).
- Each player additionally subscribes to `/topic/games/{gameId}/private/{sessionId}`, which delivers `PrivateHandUpdate` containing their actual `CardView[]` hand.
- The frontend merges these two streams to render: own hand as card faces, opponents as fanned backs + count badge.

---

## Backend

### Lobby Layer

#### Domain

```
LobbyState
  code           String          e.g. "UNO-7X3K" (4-char alphanumeric after prefix)
  hostSessionId  String
  players        List<LobbyPlayer>   ordered; index = playerIndex in the eventual game
  status         WAITING | IN_PROGRESS
  gameId         String              null until host starts

LobbyPlayer
  sessionId      String
  displayName    String
  playerIndex    int
```

Lobby codes are generated as `"UNO-" + 4 random uppercase alphanumeric chars`, checked for uniqueness before assignment.

#### New Service: `LobbySessionService`

Manages all lobbies in a `ConcurrentHashMap<String, LobbyState>`. Responsibilities:

- `createLobby(sessionId, displayName)` → creates lobby, assigns host as player 0, returns lobby
- `joinLobby(code, sessionId, displayName)` → adds player, returns updated lobby (throws if full or already in progress)
- `startGame(code, sessionId)` → validates caller is host, creates `GameState` via `GameSessionService`, transitions lobby to `IN_PROGRESS`, returns `gameId`
- `getLobby(code)` → returns `Optional<LobbyState>`

#### New REST Endpoints

| Method | Path | Body | Response |
|--------|------|------|----------|
| `POST` | `/api/lobbies` | `{ displayName }` | `{ code, playerIndex: 0, lobby }` |
| `POST` | `/api/lobbies/{code}/join` | `{ displayName }` | `{ playerIndex, lobby }` |
| `POST` | `/api/lobbies/{code}/start` | _(empty)_ | `{ gameId }` |
| `GET`  | `/api/lobbies/{code}` | — | `LobbySnapshot` |

`sessionId` is read from the `X-Session-Id` request header on all lobby endpoints.

### GameState Changes

`initializeGame()` becomes `initializeGame(List<String> playerNames)` — creates one `Player` per name, deals 7 cards each. No other game logic changes; the existing turn/direction/stacking system handles N players correctly.

`GameSessionService` gains:
- `Map<String, Map<String, Integer>>` — `gameId → (sessionId → playerIndex)` for validating callers
- `createGame(List<LobbyPlayer> players)` called by `LobbySessionService.startGame()`

Existing play/draw/pass endpoints add `sessionId` header validation: the server checks that the `playerIndex` in the request body matches the sessionId's assigned slot.

### WebSocket Layer

#### `WebSocketConfig`

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    // SockJS endpoint at /ws — allowed origins from CorsProperties (same list as /api/**)
    // Simple broker on /topic
    // App destination prefix /app
}
```

`CorsProperties.allowedOrigins` is reused for the SockJS endpoint so there is a single source of truth for allowed origins.

#### `GameBroadcastService`

Called after every game mutation. Uses `SimpMessagingTemplate` to:
1. Push `PublicGameSnapshot` to `/topic/games/{gameId}`
2. For each player, push `PrivateHandUpdate` to `/topic/games/{gameId}/private/{sessionId}`

#### `LobbyBroadcastService`

Called after every lobby mutation. Pushes `LobbySnapshot` to `/topic/lobbies/{code}`.

### New / Changed DTOs

| DTO | Contents |
|-----|----------|
| `PublicGameSnapshot` | All fields from current `GameSnapshotResponse`, but `players[i].hand` is absent; `players[i].handSize` replaces it |
| `PrivateHandUpdate` | `gameId`, `playerIndex`, `hand: CardView[]` |
| `LobbySnapshot` | `code`, `hostPlayerIndex`, `players` (name + index), `status`, `gameId` |
| `CreateLobbyResponse` | `code`, `playerIndex`, `lobby: LobbySnapshot` |
| `JoinLobbyResponse` | `playerIndex`, `lobby: LobbySnapshot` |
| `StartGameResponse` | `gameId` |

---

## Frontend

### Screen Flow

```
Home
  ├─ UsernameBar (top-right, always visible, editable)
  ├─ "Create Game" → POST /api/lobbies → navigate to /lobby/{code}
  └─ code input + "Join Game" → POST /api/lobbies/{code}/join → navigate to /lobby/{code}

Lobby  (/lobby/{code})
  ├─ code displayed prominently with one-click copy button
  ├─ live player list via /topic/lobbies/{code}
  ├─ host: "Start Game" button (enabled when ≥ 2 players)
  └─ on LobbySnapshot.status === IN_PROGRESS → navigate to /game/{gameId}

Game  (/game/{gameId})
  ├─ subscribe /topic/games/{gameId} → public state
  ├─ subscribe /topic/games/{gameId}/private/{sessionId} → own hand
  └─ play/draw/pass → REST POST (+ X-Session-Id header)
```

Client-side routing via React Router (`BrowserRouter`). Routes: `/`, `/lobby/:code`, `/game/:gameId`.

### WebSocket Hook: `useStompClient`

Replaces the `setInterval` polling in `useGame.ts`. Wraps `@stomp/stompjs` + `sockjs-client`:

- Connects on mount, disconnects on unmount (cleanup)
- Accepts subscription topics + callbacks
- Exposes `{ connected, subscribe, unsubscribe }`

### Page Refresh / Reconnection

When a player refreshes mid-game, the browser retains `sessionId` and `displayName` from localStorage and the URL still contains `gameId`. On mount, `useGame` calls `GET /api/games/{gameId}` (existing REST endpoint) to load the current public snapshot immediately, then establishes the STOMP subscriptions. The server looks up `sessionId → playerIndex` from the in-memory map and pushes a `PrivateHandUpdate` to that player's private topic on first subscription so the hand is never blank.

For the lobby: if a player refreshes on `/lobby/{code}`, `GET /api/lobbies/{code}` fetches the current lobby state synchronously, then the STOMP subscription resumes live updates.

### Game Hook: `useGame` (refactored)

Keeps the same public API (`{ snapshot, playCard, drawCard, passTurn, busy, error }`) so `GameTable.tsx` changes are scoped to layout only. Internally:

- Subscribes to public topic → updates `publicSnapshot`
- Subscribes to private topic → updates `privateHand`
- Merges them: local player's hand comes from `privateHand`; all other players use `handSize` from `publicSnapshot`
- Adds `X-Session-Id` header to all REST calls

### Circular Table Layout (`GameTable.tsx`)

The table is a fixed-aspect-ratio container (e.g. 16:9). Player slots are absolutely positioned using polar coordinates:

- Local player anchored at 270° (bottom center)
- `N-1` opponents distributed clockwise starting from just past 270°
- Angle step = `360 / N`

For each opponent slot, a new `OpponentSlot` component renders:
- Display name
- Fanned card backs (up to ~7 visible, scaled to actual hand size)
- Hand count badge
- Active-turn highlight ring

The existing `PlayerHand` component is reused unchanged at the bottom for the local player.

### New / Changed Components

| Component | Change |
|-----------|--------|
| `useGame.ts` | Replace `setInterval` with STOMP subscriptions; merge public + private streams |
| `GameTable.tsx` | Replace fixed 2-slot layout with polar-coordinate N-player layout |
| `Card.tsx` | Add `face="back"` prop for rendering the card back image |
| `GameSetup.tsx` | Replace with `Home.tsx` |
| New: `UsernameBar.tsx` | Persistent name display + inline edit; saves to localStorage |
| New: `LobbyRoom.tsx` | Waiting room: code, player list, start button |
| New: `OpponentSlot.tsx` | One opponent position: fanned backs + count badge + name + active ring |
| New: `useStompClient.ts` | STOMP connection lifecycle hook |

### New npm Dependencies

- `@stomp/stompjs` — STOMP protocol client
- `sockjs-client` + `@types/sockjs-client` — SockJS transport
- `react-router-dom` — client-side routing

---

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Join a full lobby (10 players) | 409 from REST; toast on frontend |
| Join a lobby that's already started | 409 from REST; toast |
| Play out of turn (wrong playerIndex vs sessionId) | 403 from REST; toast |
| WebSocket disconnects mid-game | Auto-reconnect with exponential backoff (built into `@stomp/stompjs`) |
| Lobby code not found | 404 from REST; error message on Home screen |

---

## Out of Scope

- Server-side persistence (restart loses all games — acceptable for now)
- Reconnection recovery (player who disconnects loses their slot)
- Spectator mode
- Chat
- Player kick/removal by host
