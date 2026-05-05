# Play Again / Lobby Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After a game finishes, players can click "Play Again" to return to the same lobby (reset to WAITING) or "Leave Lobby" to go home; host status is preserved with lowest-index promotion on leave.

**Architecture:** Explicit reset endpoint (idempotent, any player can call); leave endpoint removes player and promotes host if needed; frontend stores lobby code in sessionStorage and computes new game index from array position on each start.

**Tech Stack:** Spring Boot 4 (backend), React 18 + TypeScript + Vite (frontend), STOMP/WebSocket for lobby broadcasts.

---

## File Map

**Backend — modified:**
- `backend/src/main/java/com/kkhuu131/uno/model/LobbyState.java`
- `backend/src/main/java/com/kkhuu131/uno/backend/service/LobbySessionService.java`
- `backend/src/main/java/com/kkhuu131/uno/backend/web/LobbyController.java`

**Backend — created:**
- `backend/src/test/java/com/kkhuu131/uno/model/LobbyStateTest.java`
- `backend/src/test/java/com/kkhuu131/uno/backend/web/LobbyControllerTest.java`

**Backend — modified (tests):**
- `backend/src/test/java/com/kkhuu131/uno/backend/service/LobbySessionServiceTest.java`

**Frontend — modified:**
- `frontend/src/utils/session.ts`
- `frontend/src/api/client.ts`
- `frontend/src/components/LobbyRoom.tsx`
- `frontend/src/components/GameOver.tsx`
- `frontend/src/components/GameTable.tsx`
- `frontend/src/index.css` (wherever `.btn--newgame` is defined)

---

## Task 1: LobbyState — mutable host + removePlayer + resetForRematch

**Files:**
- Modify: `backend/src/main/java/com/kkhuu131/uno/model/LobbyState.java`
- Create: `backend/src/test/java/com/kkhuu131/uno/model/LobbyStateTest.java`

- [ ] **Step 1: Write failing tests**

Create `backend/src/test/java/com/kkhuu131/uno/model/LobbyStateTest.java`:

```java
package com.kkhuu131.uno.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LobbyStateTest {

    @Test
    void removePlayer_removesFromList() {
        LobbyState lobby = new LobbyState("UNO-TEST", "s0");
        lobby.addPlayer(new LobbyPlayer("s0", "Alice", 0));
        lobby.addPlayer(new LobbyPlayer("s1", "Bob", 1));

        lobby.removePlayer("s1");

        assertThat(lobby.getPlayers()).hasSize(1);
        assertThat(lobby.getPlayers().get(0).sessionId()).isEqualTo("s0");
    }

    @Test
    void removePlayer_nonHost_preservesHost() {
        LobbyState lobby = new LobbyState("UNO-TEST", "s0");
        lobby.addPlayer(new LobbyPlayer("s0", "Alice", 0));
        lobby.addPlayer(new LobbyPlayer("s1", "Bob", 1));

        lobby.removePlayer("s1");

        assertThat(lobby.getHostSessionId()).isEqualTo("s0");
    }

    @Test
    void removePlayer_host_promotesLowestRemainingIndex() {
        LobbyState lobby = new LobbyState("UNO-TEST", "s0");
        lobby.addPlayer(new LobbyPlayer("s0", "Alice", 0));
        lobby.addPlayer(new LobbyPlayer("s1", "Bob", 1));
        lobby.addPlayer(new LobbyPlayer("s2", "Carol", 2));

        lobby.removePlayer("s0");

        assertThat(lobby.getHostSessionId()).isEqualTo("s1");
    }

    @Test
    void removePlayer_host_promotesLowestAmongNonContiguousIndices() {
        LobbyState lobby = new LobbyState("UNO-TEST", "s0");
        lobby.addPlayer(new LobbyPlayer("s0", "Alice", 0));
        lobby.addPlayer(new LobbyPlayer("s1", "Bob", 1));
        lobby.addPlayer(new LobbyPlayer("s2", "Carol", 2));
        lobby.removePlayer("s1"); // Bob leaves first; now indices 0, 2
        lobby.removePlayer("s0"); // Host leaves; Carol (index 2) should be promoted

        assertThat(lobby.getHostSessionId()).isEqualTo("s2");
    }

    @Test
    void removePlayer_lastPlayer_leavesEmptyList() {
        LobbyState lobby = new LobbyState("UNO-TEST", "s0");
        lobby.addPlayer(new LobbyPlayer("s0", "Alice", 0));

        lobby.removePlayer("s0");

        assertThat(lobby.getPlayers()).isEmpty();
    }

    @Test
    void resetForRematch_setsWaitingAndClearsGameId() {
        LobbyState lobby = new LobbyState("UNO-TEST", "s0");
        lobby.addPlayer(new LobbyPlayer("s0", "Alice", 0));
        lobby.addPlayer(new LobbyPlayer("s1", "Bob", 1));
        lobby.start("game-123");

        lobby.resetForRematch();

        assertThat(lobby.getStatus()).isEqualTo(LobbyStatus.WAITING);
        assertThat(lobby.getGameId()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
.\mvnw.cmd test -pl backend -Dtest="LobbyStateTest" -DfailIfNoTests=false
```

Expected: compilation errors (methods don't exist yet).

- [ ] **Step 3: Implement changes in LobbyState.java**

Replace the full file content:

```java
package com.kkhuu131.uno.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LobbyState {

    private final String code;
    private String hostSessionId;
    private final List<LobbyPlayer> players = new ArrayList<>();
    private LobbyStatus status = LobbyStatus.WAITING;
    private String gameId;

    public LobbyState(String code, String hostSessionId) {
        this.code = code;
        this.hostSessionId = hostSessionId;
    }

    public String getCode() { return code; }

    public String getHostSessionId() { return hostSessionId; }

    public synchronized LobbyStatus getStatus() { return status; }

    public synchronized String getGameId() { return gameId; }

    public synchronized List<LobbyPlayer> getPlayers() {
        return List.copyOf(players);
    }

    public synchronized int getPlayerCount() {
        return players.size();
    }

    public synchronized boolean hasSession(String sessionId) {
        return players.stream().anyMatch(p -> p.sessionId().equals(sessionId));
    }

    public synchronized void addPlayer(LobbyPlayer player) {
        players.add(player);
    }

    public synchronized void removePlayer(String sessionId) {
        players.removeIf(p -> p.sessionId().equals(sessionId));
        if (players.isEmpty()) return;
        boolean hostStillPresent = players.stream()
                .anyMatch(p -> p.sessionId().equals(hostSessionId));
        if (!hostStillPresent) {
            hostSessionId = players.stream()
                    .min(Comparator.comparingInt(LobbyPlayer::playerIndex))
                    .map(LobbyPlayer::sessionId)
                    .orElseThrow();
        }
    }

    public synchronized void resetForRematch() {
        this.status = LobbyStatus.WAITING;
        this.gameId = null;
    }

    public synchronized void start(String gameId) {
        this.status = LobbyStatus.IN_PROGRESS;
        this.gameId = gameId;
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```
.\mvnw.cmd test -pl backend -Dtest="LobbyStateTest" -DfailIfNoTests=false
```

Expected: BUILD SUCCESS, 6 tests passing.

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/kkhuu131/uno/model/LobbyState.java backend/src/test/java/com/kkhuu131/uno/model/LobbyStateTest.java
git commit -m "feat(backend): LobbyState removePlayer with host promotion and resetForRematch"
```

---

## Task 2: LobbySessionService — leaveLobby + resetLobby

**Files:**
- Modify: `backend/src/main/java/com/kkhuu131/uno/backend/service/LobbySessionService.java`
- Modify: `backend/src/test/java/com/kkhuu131/uno/backend/service/LobbySessionServiceTest.java`

- [ ] **Step 1: Add failing tests to LobbySessionServiceTest.java**

Append these test methods inside the existing `LobbySessionServiceTest` class (before the closing `}`):

```java
    @Test
    void leaveLobby_removesPlayer() {
        LobbyState lobby = lobbySessionService.createLobby("s-leave-host", "Host");
        lobbySessionService.joinLobby(lobby.getCode(), "s-leave-guest", "Guest");

        lobbySessionService.leaveLobby(lobby.getCode(), "s-leave-guest");

        LobbyState updated = lobbySessionService.findLobby(lobby.getCode()).orElseThrow();
        assertThat(updated.getPlayers()).hasSize(1);
        assertThat(updated.getPlayers().get(0).sessionId()).isEqualTo("s-leave-host");
    }

    @Test
    void leaveLobby_lastPlayer_removesLobby() {
        LobbyState lobby = lobbySessionService.createLobby("s-last-only", "Solo");
        String code = lobby.getCode();

        lobbySessionService.leaveLobby(code, "s-last-only");

        assertThat(lobbySessionService.findLobby(code)).isEmpty();
    }

    @Test
    void resetLobby_whenAlreadyWaiting_isNoOp() {
        LobbyState lobby = lobbySessionService.createLobby("s-reset-wait", "Host");

        LobbyState result = lobbySessionService.resetLobby(lobby.getCode(), gameSessionService);

        assertThat(result.getStatus()).isEqualTo(LobbyStatus.WAITING);
    }

    @Test
    void resetLobby_withUnfinishedGame_isNoOp() {
        LobbyState lobby = lobbySessionService.createLobby("s-reset-noop-h", "Host");
        lobbySessionService.joinLobby(lobby.getCode(), "s-reset-noop-g", "Guest");
        lobbySessionService.startGame(lobby.getCode(), "s-reset-noop-h", gameSessionService);

        LobbyState result = lobbySessionService.resetLobby(lobby.getCode(), gameSessionService);

        assertThat(result.getStatus()).isEqualTo(LobbyStatus.IN_PROGRESS);
    }

    @Test
    void resetLobby_withFinishedGame_resetsToWaiting() {
        LobbyState lobby = lobbySessionService.createLobby("s-reset-fin-h", "Host");
        lobbySessionService.joinLobby(lobby.getCode(), "s-reset-fin-g", "Guest");
        String gameId = lobbySessionService.startGame(lobby.getCode(), "s-reset-fin-h", gameSessionService);

        // Force game into finished state via reflection
        com.kkhuu131.uno.model.GameState game =
                gameSessionService.findGame(gameId).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(game, "hasWinner", true);

        LobbyState result = lobbySessionService.resetLobby(lobby.getCode(), gameSessionService);

        assertThat(result.getStatus()).isEqualTo(LobbyStatus.WAITING);
        assertThat(result.getGameId()).isNull();
    }
```

- [ ] **Step 2: Run tests to confirm they fail**

```
.\mvnw.cmd test -pl backend -Dtest="LobbySessionServiceTest" -DfailIfNoTests=false
```

Expected: compilation errors (`leaveLobby`, `resetLobby` not defined).

- [ ] **Step 3: Add leaveLobby and resetLobby to LobbySessionService.java**

Add these two methods and the new import at the top of `LobbySessionService.java`. Add after the existing `findLobby` method:

Add import at the top of the file (with the other imports):
```java
import com.kkhuu131.uno.model.GameState;
```

Add methods:
```java
    public void leaveLobby(String code, String sessionId) {
        LobbyState lobby = lobbies.get(code);
        if (lobby == null) return;
        lobby.removePlayer(sessionId);
        if (lobby.getPlayerCount() == 0) {
            lobbies.remove(code);
        }
    }

    public LobbyState resetLobby(String code, GameSessionService gameSessionService) {
        LobbyState lobby = requireLobby(code);
        if (lobby.getStatus() != LobbyStatus.IN_PROGRESS) return lobby;
        boolean gameFinished = gameSessionService.findGame(lobby.getGameId())
                .map(GameState::hasWinner)
                .orElse(false);
        if (!gameFinished) return lobby;
        lobby.resetForRematch();
        return lobby;
    }
```

- [ ] **Step 4: Run tests to confirm they pass**

```
.\mvnw.cmd test -pl backend -Dtest="LobbySessionServiceTest" -DfailIfNoTests=false
```

Expected: BUILD SUCCESS, all tests passing.

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/kkhuu131/uno/backend/service/LobbySessionService.java backend/src/test/java/com/kkhuu131/uno/backend/service/LobbySessionServiceTest.java
git commit -m "feat(backend): LobbySessionService leaveLobby and resetLobby"
```

---

## Task 3: LobbyController — /leave and /reset endpoints

**Files:**
- Modify: `backend/src/main/java/com/kkhuu131/uno/backend/web/LobbyController.java`
- Create: `backend/src/test/java/com/kkhuu131/uno/backend/web/LobbyControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `backend/src/test/java/com/kkhuu131/uno/backend/web/LobbyControllerTest.java`:

```java
package com.kkhuu131.uno.backend.web;

import com.kkhuu131.uno.backend.exception.LobbyNotFoundException;
import com.kkhuu131.uno.backend.service.GameSessionService;
import com.kkhuu131.uno.backend.service.LobbyBroadcastService;
import com.kkhuu131.uno.backend.service.LobbySessionService;
import com.kkhuu131.uno.model.LobbyPlayer;
import com.kkhuu131.uno.model.LobbyState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LobbyControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean LobbySessionService lobbySessionService;
    @MockitoBean GameSessionService gameSessionService;
    @MockitoBean LobbyBroadcastService broadcastService;

    private LobbyState twoPlayerLobby(String code, String hostId) {
        LobbyState lobby = new LobbyState(code, hostId);
        lobby.addPlayer(new LobbyPlayer(hostId, "Host", 0));
        lobby.addPlayer(new LobbyPlayer("s1", "Guest", 1));
        return lobby;
    }

    @Test
    void leaveLobby_returns204_andBroadcasts_whenLobbyStillExists() throws Exception {
        LobbyState lobby = twoPlayerLobby("UNO-ABCD", "s0");
        doNothing().when(lobbySessionService).leaveLobby(eq("UNO-ABCD"), eq("s0"));
        when(lobbySessionService.findLobby("UNO-ABCD")).thenReturn(Optional.of(lobby));

        mvc.perform(post("/api/lobbies/UNO-ABCD/leave")
                .header("X-Session-Id", "s0"))
                .andExpect(status().isNoContent());

        verify(broadcastService).broadcastUpdate(lobby);
    }

    @Test
    void leaveLobby_returns204_noBroadcast_whenLobbyGone() throws Exception {
        doNothing().when(lobbySessionService).leaveLobby(eq("UNO-ABCD"), eq("s0"));
        when(lobbySessionService.findLobby("UNO-ABCD")).thenReturn(Optional.empty());

        mvc.perform(post("/api/lobbies/UNO-ABCD/leave")
                .header("X-Session-Id", "s0"))
                .andExpect(status().isNoContent());

        verify(broadcastService, never()).broadcastUpdate(any());
    }

    @Test
    void resetLobby_returns200WithSnapshot_andBroadcasts() throws Exception {
        LobbyState lobby = twoPlayerLobby("UNO-EFGH", "s0");
        when(lobbySessionService.resetLobby(eq("UNO-EFGH"), any(GameSessionService.class)))
                .thenReturn(lobby);

        mvc.perform(post("/api/lobbies/UNO-EFGH/reset")
                .header("X-Session-Id", "s0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("UNO-EFGH"));

        verify(broadcastService).broadcastUpdate(lobby);
    }

    @Test
    void resetLobby_notFound_returns404() throws Exception {
        when(lobbySessionService.resetLobby(eq("UNO-XXXX"), any(GameSessionService.class)))
                .thenThrow(new LobbyNotFoundException("UNO-XXXX"));

        mvc.perform(post("/api/lobbies/UNO-XXXX/reset")
                .header("X-Session-Id", "s0"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
.\mvnw.cmd test -pl backend -Dtest="LobbyControllerTest" -DfailIfNoTests=false
```

Expected: 404s for the new endpoints (routes don't exist yet).

- [ ] **Step 3: Add /leave and /reset endpoints to LobbyController.java**

Add these two methods inside `LobbyController`, after the existing `getLobby` method. Also add the `@ResponseStatus` import:

Add to the import block:
```java
import org.springframework.web.bind.annotation.ResponseStatus;
```

Add methods:
```java
    @PostMapping("/{code}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveLobby(
            @PathVariable String code,
            @RequestHeader("X-Session-Id") String sessionId) {
        lobbySessionService.leaveLobby(code, sessionId);
        lobbySessionService.findLobby(code).ifPresent(broadcastService::broadcastUpdate);
    }

    @PostMapping("/{code}/reset")
    public ResponseEntity<LobbySnapshot> resetLobby(
            @PathVariable String code,
            @RequestHeader("X-Session-Id") String sessionId) {
        LobbyState lobby = lobbySessionService.resetLobby(code, gameSessionService);
        broadcastService.broadcastUpdate(lobby);
        return ResponseEntity.ok(snapshotMapper.toSnapshot(lobby));
    }
```

- [ ] **Step 4: Run tests to confirm they pass**

```
.\mvnw.cmd test -pl backend -Dtest="LobbyControllerTest" -DfailIfNoTests=false
```

Expected: BUILD SUCCESS, 4 tests passing.

- [ ] **Step 5: Run the full backend test suite**

```
.\mvnw.cmd test -pl backend
```

Expected: BUILD SUCCESS, all existing tests still passing.

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/kkhuu131/uno/backend/web/LobbyController.java backend/src/test/java/com/kkhuu131/uno/backend/web/LobbyControllerTest.java
git commit -m "feat(backend): add /leave and /reset lobby endpoints"
```

---

## Task 4: session.ts — lobby code helpers

**Files:**
- Modify: `frontend/src/utils/session.ts`

- [ ] **Step 1: Add lobby code helpers**

Append to the end of `frontend/src/utils/session.ts`:

```typescript
const LOBBY_CODE_KEY = 'uno_lobby_code'

export function getLobbyCode(): string | null {
  return sessionStorage.getItem(LOBBY_CODE_KEY)
}

export function setLobbyCode(code: string): void {
  sessionStorage.setItem(LOBBY_CODE_KEY, code)
}

export function clearLobbyCode(): void {
  sessionStorage.removeItem(LOBBY_CODE_KEY)
}
```

- [ ] **Step 2: Commit**

```
git add frontend/src/utils/session.ts
git commit -m "feat(frontend): add getLobbyCode/setLobbyCode/clearLobbyCode session helpers"
```

---

## Task 5: api/client.ts — resetLobby + leaveLobby + void response fix

**Files:**
- Modify: `frontend/src/api/client.ts`

- [ ] **Step 1: Fix request() to handle 204 No Content, and add new methods**

In `frontend/src/api/client.ts`, make two changes:

**Change 1** — add a 204 guard in the `request` function, replacing the final `return res.json()` line:

```typescript
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
```

**Change 2** — add `resetLobby` and `leaveLobby` to the `api` object, after `getLobby`:

```typescript
  resetLobby: (code: string) =>
    request<LobbySnapshot>(`/lobbies/${code}/reset`, { method: 'POST' }),

  leaveLobby: (code: string) =>
    request<void>(`/lobbies/${code}/leave`, { method: 'POST' }),
```

- [ ] **Step 2: Commit**

```
git add frontend/src/api/client.ts
git commit -m "feat(frontend): add resetLobby and leaveLobby API methods"
```

---

## Task 6: LobbyRoom.tsx — store lobby code + index remapping + handleStart fix

**Files:**
- Modify: `frontend/src/components/LobbyRoom.tsx`

- [ ] **Step 1: Add setLobbyCode import and call it on mount**

Add `setLobbyCode` to the session import at the top of `LobbyRoom.tsx`:

```typescript
import { getSessionId, setLobbyCode } from '../utils/session'
```

In the initial REST load `useEffect`, add `setLobbyCode(code)` as the first line:

```typescript
  useEffect(() => {
    if (!code) return
    setLobbyCode(code)
    api.getLobby(code)
      .then(setLobby)
      .catch(() => setError('Lobby not found'))
  }, [code])
```

- [ ] **Step 2: Fix STOMP handler — compute game index from array position**

Replace the existing `useStompClient` call with:

```typescript
  useStompClient(
    code
      ? [{ topic: `/topic/lobbies/${code}`, onMessage: (body) => {
          const snap = body as LobbySnapshot
          setLobby(snap)
          if (snap.status === 'IN_PROGRESS' && snap.gameId) {
            const myLobbyIndex = parseInt(sessionStorage.getItem('uno_player_index') ?? '-1')
            const myGameIndex = snap.players.findIndex(p => p.playerIndex === myLobbyIndex)
            const gameIndex = myGameIndex >= 0 ? myGameIndex : 0
            sessionStorage.setItem('uno_player_index', String(gameIndex))
            navigate('/game/' + snap.gameId, { state: { playerIndex: gameIndex } })
          }
        }}]
      : []
  )
```

- [ ] **Step 3: Simplify handleStart — remove duplicate navigate, rely on STOMP**

Replace the existing `handleStart` function with:

```typescript
  async function handleStart() {
    if (!code) return
    setStarting(true)
    setError(null)
    try {
      await api.startGame(code)
      // Navigation is handled by the STOMP broadcast when lobby status → IN_PROGRESS
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to start game')
      setStarting(false)
    }
  }
```

- [ ] **Step 4: Commit**

```
git add frontend/src/components/LobbyRoom.tsx
git commit -m "feat(frontend): store lobby code, fix game-index remapping, unify start navigation via STOMP"
```

---

## Task 7: GameOver.tsx + GameTable.tsx + CSS

**Files:**
- Modify: `frontend/src/components/GameOver.tsx`
- Modify: `frontend/src/components/GameTable.tsx`
- Modify: CSS file where `.btn--newgame` is defined

- [ ] **Step 1: Update GameOver.tsx to accept optional lobby buttons**

Replace the full contents of `frontend/src/components/GameOver.tsx`:

```typescript
import { motion } from 'framer-motion'
import { AutoConfetti } from './Confetti'

interface Props {
  winnerName: string
  onNewGame: () => void
  onPlayAgain?: () => void
  onLeaveLobby?: () => void
}

export function GameOver({ winnerName, onNewGame, onPlayAgain, onLeaveLobby }: Props) {
  return (
    <>
      <AutoConfetti active />
      <div className="gameover-overlay">
        <motion.div
          className="gameover"
          initial={{ scale: 0.6, opacity: 0, y: 32 }}
          animate={{ scale: 1, opacity: 1, y: 0 }}
          transition={{ type: 'spring', stiffness: 300, damping: 22 }}
        >
          <motion.div
            className="gameover__trophy"
            animate={{ rotate: [-8, 8, -8] }}
            transition={{ repeat: Infinity, duration: 2, ease: 'easeInOut' }}
          >
            🏆
          </motion.div>

          <h1 className="gameover__title">Game Over!</h1>

          <div className="gameover__winner">
            <span className="gameover__winner-label">Winner</span>
            <motion.span
              className="gameover__winner-name"
              initial={{ scale: 0.7 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', stiffness: 280, damping: 16, delay: 0.15 }}
            >
              🎉 {winnerName}
            </motion.span>
          </div>

          {onPlayAgain && onLeaveLobby ? (
            <div className="gameover__actions">
              <motion.button
                className="btn btn--newgame"
                onClick={onPlayAgain}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.96 }}
              >
                Play Again
              </motion.button>
              <motion.button
                className="btn btn--leave"
                onClick={onLeaveLobby}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.96 }}
              >
                Leave Lobby
              </motion.button>
            </div>
          ) : (
            <motion.button
              className="btn btn--newgame"
              onClick={onNewGame}
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.96 }}
            >
              New Game
            </motion.button>
          )}
        </motion.div>
      </div>
    </>
  )
}
```

- [ ] **Step 2: Wire up handlers in GameTable.tsx**

Add two imports to `GameTable.tsx` (alongside existing imports):

```typescript
import { api } from '../api/client'
import { clearLobbyCode, getLobbyCode } from '../utils/session'
```

Add these lines inside the `GameTable` component function, right after `const navigate = useNavigate()`:

```typescript
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
```

Replace the existing `GameOver` render at the bottom of `GameTable.tsx`:

```typescript
      {status === 'FINISHED' && winnerName && (
        <GameOver
          winnerName={winnerName}
          onNewGame={() => navigate('/')}
          onPlayAgain={lobbyCode ? handlePlayAgain : undefined}
          onLeaveLobby={lobbyCode ? handleLeaveLobby : undefined}
        />
      )}
```

- [ ] **Step 3: Add CSS for the new buttons**

Find the CSS file where `.btn--newgame` is defined (search with `grep -r "btn--newgame" frontend/src`). Add the following styles near `.btn--newgame`:

```css
.gameover__actions {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  width: 100%;
}

.btn--leave {
  background: transparent;
  color: var(--text-muted, #888);
  border: 1.5px solid currentColor;
  border-radius: 10px;
  padding: 0.65rem 1.5rem;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  width: 100%;
}

.btn--leave:hover {
  color: var(--text, #ccc);
  border-color: currentColor;
}
```

- [ ] **Step 4: Start the dev server and manually verify the full flow**

```
cd frontend && npm run dev
```

Test the golden path:
1. Create a lobby, join with a second browser tab
2. Start the game, play until someone wins
3. Verify "Play Again" and "Leave Lobby" buttons appear on the game over screen
4. Click "Play Again" → confirm both players land on the lobby screen (WAITING state), same code shown
5. Start the game again → confirm each player has the correct player index (check turn order matches lobby order)
6. Go back to a finished game, click "Leave Lobby" → confirm you land on home screen and the other player sees an updated lobby with one fewer player

Test with a player who joined second (index 1) leaving the lobby after "Play Again":
7. Player 0 clicks Play Again, Player 1 clicks Leave Lobby
8. Player 0 should see the lobby with only themselves; they are now host (were already)
9. Player 0 cannot start (only 1 player); lobby shows correct state

- [ ] **Step 5: Commit**

```
git add frontend/src/components/GameOver.tsx frontend/src/components/GameTable.tsx frontend/src/index.css
git commit -m "feat(frontend): Play Again and Leave Lobby buttons on game over screen"
```
