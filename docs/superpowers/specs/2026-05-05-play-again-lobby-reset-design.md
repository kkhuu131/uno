# Play Again / Lobby Reset — Design Spec

**Date:** 2026-05-05  
**Status:** Approved

## Summary

After a game finishes, players see a `GameOver` screen with two new buttons: **Play Again** (returns to the same lobby in `WAITING` state) and **Leave Lobby** (removes the player from the lobby and goes home). Each player decides independently. Host status is preserved; if the host leaves, the remaining player with the lowest `playerIndex` is promoted.

Abandoned lobby cleanup (TTL, idle eviction) is explicitly out of scope and deferred as a separate task.

---

## Backend

### `LobbyState.java`

- Change `hostSessionId` from `final String` to a mutable `String` field.
- Add `removePlayer(String sessionId)`:
  - Removes the matching `LobbyPlayer` from the players list.
  - If the removed player was host and other players remain, sets `hostSessionId` to the `sessionId` of the remaining player with the lowest `playerIndex`.
  - If no players remain, takes no further action (caller handles cleanup).
- Add `resetForRematch()`:
  - Sets `status = LobbyStatus.WAITING`.
  - Sets `gameId = null`.

### `LobbySessionService.java`

- Add `leaveLobby(String code, String sessionId)`:
  - Looks up lobby by code; returns if not found.
  - Calls `lobby.removePlayer(sessionId)`.
  - If lobby is now empty, removes it from the `lobbies` map and returns (no broadcast).
  - Otherwise, broadcasts the updated `LobbySnapshot` via `LobbyBroadcastService`.
- Add `resetLobby(String code, String sessionId, GameSessionService gameSessionService)`:
  - Looks up lobby by code; returns current state if not found.
  - No-op (returns current state) if `lobby.status != IN_PROGRESS`.
  - No-op if the associated game is not finished (`gameSessionService.findGame(lobby.getGameId())` has no winner).
  - Calls `lobby.resetForRematch()`.
  - Broadcasts the updated `LobbySnapshot`.
  - Returns the updated snapshot.

### `LobbyController.java`

Two new endpoints, both reading `sessionId` from the session cookie (same pattern as existing endpoints):

| Method | Path | Behavior |
|--------|------|----------|
| `POST` | `/api/lobbies/{code}/leave` | Calls `leaveLobby`; returns `204 No Content` |
| `POST` | `/api/lobbies/{code}/reset` | Calls `resetLobby`; returns updated `LobbySnapshot` |

---

## Frontend

### `session.ts`

Add three helpers using `sessionStorage`:

```ts
getLobbyCode(): string | null
setLobbyCode(code: string): void
clearLobbyCode(): void
```

### `LobbyRoom.tsx`

- On mount: call `setLobbyCode(code)` (the code comes from the URL param).
- When the lobby transitions to `IN_PROGRESS` and navigation to the game fires:
  - Compute the player's **new game index** as their position (0-based) in `lobbySnapshot.players`, matched by their stored `playerIndex`. This handles the case where a player left between the last game ending and the new one starting, shifting indices.
  - Update `sessionStorage.uno_player_index` to this new game index before navigating.
- When a player navigates back via "Play Again," they are still in the lobby's player list (`leaveLobby` was not called). `LobbyRoom` must **not** call any join endpoint on mount — it only fetches current state and subscribes to STOMP.

### `api/client.ts`

```ts
resetLobby(code: string): Promise<LobbySnapshot>
leaveLobby(code: string): Promise<void>
```

### `GameOver.tsx`

Reads `lobbyCode = getLobbyCode()` from session.

- If `lobbyCode` is present (came through a real lobby):
  - Show **Play Again**: calls `api.resetLobby(lobbyCode)` then navigates to `/lobby/{lobbyCode}`.
  - Show **Leave Lobby**: calls `api.leaveLobby(lobbyCode)`, calls `clearLobbyCode()`, then navigates to `/`.
- If `lobbyCode` is absent (hot-seat or direct URL): show existing **New Game** button only.

`GameTable.tsx` passes `onPlayAgain` and `onLeaveLobby` callbacks into `GameOver`, keeping API calls in `GameTable` where game/lobby context already lives.

---

## End-to-End Flow

1. Game finishes → `GameOver` screen shown to all players.
2. **Player A clicks Play Again** → `POST /api/lobbies/{code}/reset` → lobby resets to `WAITING`, broadcast fires → A navigates to `/lobby/{code}` → `LobbyRoom` mounts, fetches `WAITING` state, subscribes to STOMP.
3. **Player B clicks Play Again** → `POST /api/lobbies/{code}/reset` (no-op, already `WAITING`) → B navigates to `/lobby/{code}` → sees same waiting room.
4. **Player C clicks Leave Lobby** → `POST /api/lobbies/{code}/leave` → removed from lobby → if C was host, player with lowest remaining `playerIndex` is promoted → broadcast fires (players already on lobby page see updated list) → C navigates to `/`.
5. Host (whoever it is now) clicks **Start Game** → normal lobby start flow.

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| Last player clicks Leave Lobby | Lobby is deleted from the service map; no broadcast |
| Two players click Play Again simultaneously | Second reset call is a no-op (already `WAITING`) |
| `resetLobby` called while game still in progress | Guard in service returns current state; no reset |
| Hot-seat game (no lobby) | `getLobbyCode()` returns null; only "New Game" button shown |
| Direct URL navigation to `/game/{id}` | Same as hot-seat — graceful degradation |
| Player left mid-lobby, indices shifted | `LobbyRoom` remaps index on game start using array position |

---

## Out of Scope

- **Lobby TTL / idle cleanup**: abandoned lobbies (post-game or otherwise) are not evicted. This is a pre-existing issue and will be addressed in a separate task.
- **Kick / force-leave**: no mechanism for the host to remove players.
- **Spectator mode**: players who navigate back to a lobby mid-game cannot join as spectators.
