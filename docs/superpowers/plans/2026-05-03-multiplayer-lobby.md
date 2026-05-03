# Multiplayer Lobby Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lobby creation/joining with short codes, real-time STOMP WebSocket sync, and a circular N-player table where each player sees only their own cards.

**Architecture:** Lobby state (pre-game) lives in `LobbySessionService` alongside the existing `GameSessionService`. REST handles create/join/start; STOMP pushes `PublicGameSnapshot` (hand sizes only) to all players and `PrivateHandUpdate` to each player's personal topic. The frontend merges both streams so opponents show card backs and the local player sees full cards.

**Tech Stack:** Spring Boot 4 (STOMP/SockJS already in pom.xml), React 18, `@stomp/stompjs`, `sockjs-client`, `react-router-dom`

---

## File Map

### Backend — new files
| File | Responsibility |
|------|---------------|
| `model/LobbyStatus.java` | `WAITING \| IN_PROGRESS` enum |
| `model/LobbyPlayer.java` | Immutable record: sessionId, displayName, playerIndex |
| `model/LobbyState.java` | Thread-safe lobby entity (players list, status, gameId) |
| `backend/exception/LobbyNotFoundException.java` | 404 |
| `backend/exception/LobbyFullException.java` | 409 |
| `backend/exception/LobbyAlreadyStartedException.java` | 409 |
| `backend/exception/ForbiddenActionException.java` | 403 |
| `backend/web/dto/PrivateHandUpdate.java` | `gameId, playerIndex, hand: List<CardView>` |
| `backend/web/dto/LobbyPlayerView.java` | `playerIndex, displayName` |
| `backend/web/dto/LobbySnapshot.java` | `code, hostPlayerIndex, players, status, gameId` |
| `backend/web/dto/CreateLobbyRequest.java` | `displayName` |
| `backend/web/dto/CreateLobbyResponse.java` | `code, playerIndex, lobby` |
| `backend/web/dto/JoinLobbyRequest.java` | `displayName` |
| `backend/web/dto/JoinLobbyResponse.java` | `playerIndex, lobby` |
| `backend/web/dto/StartGameResponse.java` | `gameId` |
| `backend/service/LobbySessionService.java` | CRUD for lobbies |
| `backend/service/GameBroadcastService.java` | Push game updates via STOMP |
| `backend/service/LobbyBroadcastService.java` | Push lobby updates via STOMP |
| `backend/web/LobbyController.java` | REST endpoints for lobbies |
| `backend/web/LobbySnapshotMapper.java` | `LobbyState → LobbySnapshot` |
| `backend/config/WebSocketConfig.java` | STOMP + SockJS configuration |

### Backend — modified files
| File | Change |
|------|--------|
| `model/GameState.java` | Add `initializeGame(List<String> playerNames)` overload |
| `backend/web/dto/PlayerStateView.java` | Add `handSize` field; make `hand` nullable |
| `backend/web/GameSnapshotMapper.java` | Add `toSnapshot(gameId, state, callerIdx)` + `toPublicSnapshot(gameId, state)` |
| `backend/service/GameSessionService.java` | Add `createGame(List<LobbyPlayer>)`, sessionId↔playerIndex maps |
| `backend/exception/ApiExceptionHandler.java` | Add handlers for 4 new exceptions |
| `backend/web/GameController.java` | Validate `X-Session-Id` on mutations; call `GameBroadcastService` |
| `backend/config/WebConfig.java` | Allow `X-Session-Id` header on `/api/**` CORS |

### Frontend — new files
| File | Responsibility |
|------|---------------|
| `src/utils/session.ts` | `getSessionId()`, `getDisplayName()`, `setDisplayName()` |
| `src/hooks/useStompClient.ts` | STOMP connection lifecycle |
| `src/components/UsernameBar.tsx` | Inline-editable persistent username in top-right |
| `src/components/Home.tsx` | Landing page: create / join lobby |
| `src/components/LobbyRoom.tsx` | Waiting room: code, player list, start button |
| `src/components/OpponentSlot.tsx` | Positioned opponent: fanned card backs, count badge, active ring |

### Frontend — modified files
| File | Change |
|------|--------|
| `package.json` | Add `@stomp/stompjs`, `sockjs-client`, `@types/sockjs-client`, `react-router-dom`, `@types/react-router-dom` |
| `src/types/index.ts` | Add `PublicPlayerStateView`, `PublicGameSnapshot`, `PrivateHandUpdate`, lobby types; update `PlayerStateView` with `handSize` |
| `src/api/client.ts` | Add `X-Session-Id` header to mutations; add lobby API methods |
| `src/hooks/useGame.ts` | Replace polling with STOMP subscriptions; merge public + private streams |
| `src/components/Card.tsx` | Add `CardBack` component (exported) |
| `src/components/GameTable.tsx` | Circular N-player layout; local player at bottom |
| `src/App.tsx` | React Router with routes for `/`, `/lobby/:code`, `/game/:gameId` |
| `src/main.tsx` | Wrap with `<BrowserRouter>` |

---

## Task 1: Extend GameState for N Players

**Files:**
- Modify: `backend/src/main/java/com/kkhuu131/uno/model/GameState.java`
- Create: `backend/src/test/java/com/kkhuu131/uno/model/GameStateNPlayersTest.java`

- [ ] **Step 1: Write the failing test**

```java
// backend/src/test/java/com/kkhuu131/uno/model/GameStateNPlayersTest.java
package com.kkhuu131.uno.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GameStateNPlayersTest {

    @Test
    void initializeGame_withFourPlayers_dealsSevenCardsEach() {
        GameState state = new GameState();
        state.initializeGame(List.of("Alice", "Bob", "Carol", "Dave"));

        assertThat(state.getPlayers()).hasSize(4);
        state.getPlayers().forEach(p ->
            assertThat(p.getHand()).hasSize(7));
    }

    @Test
    void initializeGame_withTenPlayers_succeeds() {
        GameState state = new GameState();
        List<String> names = List.of("P1","P2","P3","P4","P5","P6","P7","P8","P9","P10");
        assertThatCode(() -> state.initializeGame(names)).doesNotThrowAnyException();
        assertThat(state.getPlayers()).hasSize(10);
    }

    @Test
    void initializeGame_withOnlyOnePlayer_throws() {
        GameState state = new GameState();
        assertThatThrownBy(() -> state.initializeGame(List.of("Solo")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("2–10");
    }

    @Test
    void initializeGame_withElevenPlayers_throws() {
        GameState state = new GameState();
        List<String> names = List.of("P1","P2","P3","P4","P5","P6","P7","P8","P9","P10","P11");
        assertThatThrownBy(() -> state.initializeGame(names))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run from `backend/`: `./mvnw test -pl . -Dtest=GameStateNPlayersTest -q`
Expected: compilation error — `initializeGame(List<String>)` does not exist yet.

- [ ] **Step 3: Add `initializeGame(List<String>)` to GameState**

In `GameState.java`, add the following just above the existing `initializeGame()` method:

```java
public void initializeGame(List<String> playerNames) {
    if (playerNames == null || playerNames.size() < 2 || playerNames.size() > 10) {
        throw new IllegalArgumentException("Game requires 2–10 players");
    }
    deck.clear();
    discardPile.clear();
    players.clear();
    currentPlayerIndex = 0;
    isClockwise = true;
    pendingDraw = 0;
    pendingDrawType = null;

    for (Color color : Color.values()) {
        deck.add(new NumberCard(color, 0));
        for (int i = 1; i <= 9; i++) {
            deck.add(new NumberCard(color, i));
            deck.add(new NumberCard(color, i));
        }
        for (int i = 0; i < 2; i++) {
            deck.add(new ActionCard(color, ActionType.DRAW_TWO));
            deck.add(new ActionCard(color, ActionType.SKIP));
            deck.add(new ActionCard(color, ActionType.REVERSE));
        }
    }
    for (int i = 0; i < 4; i++) {
        deck.add(new WildCard(WildType.WILD));
        deck.add(new WildCard(WildType.WILD_DRAW_FOUR));
    }
    Collections.shuffle(deck);

    for (String name : playerNames) {
        Player p = new Player(name);
        for (int j = 0; j < DEFAULT_HAND_SIZE; j++) {
            p.addToHand(deck.remove(0));
        }
        players.add(p);
    }
    placeStarterCard();
}
```

Replace the existing `initializeGame()` no-arg method with a delegation:

```java
public void initializeGame() {
    initializeGame(List.of("Player 1", "Player 2"));
}
```

Also add `import java.util.List;` to the imports.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=GameStateNPlayersTest -q`
Expected: BUILD SUCCESS, 4 tests passed.

- [ ] **Step 5: Verify no existing tests broke**

Run: `./mvnw test -pl . -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/kkhuu131/uno/model/GameState.java \
        backend/src/test/java/com/kkhuu131/uno/model/GameStateNPlayersTest.java
git commit -m "feat(model): support 2-10 players in GameState.initializeGame"
```

---

## Task 2: New Exceptions + ApiExceptionHandler

**Files:**
- Create: `backend/src/main/java/com/kkhuu131/uno/backend/exception/LobbyNotFoundException.java`
- Create: `backend/src/main/java/com/kkhuu131/uno/backend/exception/LobbyFullException.java`
- Create: `backend/src/main/java/com/kkhuu131/uno/backend/exception/LobbyAlreadyStartedException.java`
- Create: `backend/src/main/java/com/kkhuu131/uno/backend/exception/ForbiddenActionException.java`
- Modify: `backend/src/main/java/com/kkhuu131/uno/backend/exception/ApiExceptionHandler.java`

- [ ] **Step 1: Create the four exception classes**

```java
// LobbyNotFoundException.java
package com.kkhuu131.uno.backend.exception;

public class LobbyNotFoundException extends RuntimeException {
    public LobbyNotFoundException(String code) {
        super("Lobby not found: " + code);
    }
}
```

```java
// LobbyFullException.java
package com.kkhuu131.uno.backend.exception;

public class LobbyFullException extends RuntimeException {
    public LobbyFullException() {
        super("Lobby is full (max 10 players)");
    }
}
```

```java
// LobbyAlreadyStartedException.java
package com.kkhuu131.uno.backend.exception;

public class LobbyAlreadyStartedException extends RuntimeException {
    public LobbyAlreadyStartedException() {
        super("Game has already started");
    }
}
```

```java
// ForbiddenActionException.java
package com.kkhuu131.uno.backend.exception;

public class ForbiddenActionException extends RuntimeException {
    public ForbiddenActionException() {
        super("Action not allowed for this player");
    }
}
```

- [ ] **Step 2: Add handlers to ApiExceptionHandler**

Add these three handler methods to the existing `ApiExceptionHandler` class:

```java
@ExceptionHandler(LobbyNotFoundException.class)
public ResponseEntity<ErrorResponse> handleLobbyNotFound(LobbyNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
}

@ExceptionHandler({LobbyFullException.class, LobbyAlreadyStartedException.class})
public ResponseEntity<ErrorResponse> handleLobbyConflict(RuntimeException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
}

@ExceptionHandler(ForbiddenActionException.class)
public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenActionException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage()));
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./mvnw compile -pl . -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/kkhuu131/uno/backend/exception/
git commit -m "feat(exception): add lobby and forbidden action exception types"
```

---

## Task 3: Lobby Domain Models

**Files:**
- Create: `backend/src/main/java/com/kkhuu131/uno/model/LobbyStatus.java`
- Create: `backend/src/main/java/com/kkhuu131/uno/model/LobbyPlayer.java`
- Create: `backend/src/main/java/com/kkhuu131/uno/model/LobbyState.java`

- [ ] **Step 1: Create LobbyStatus**

```java
package com.kkhuu131.uno.model;

public enum LobbyStatus {
    WAITING,
    IN_PROGRESS
}
```

- [ ] **Step 2: Create LobbyPlayer**

```java
package com.kkhuu131.uno.model;

public record LobbyPlayer(String sessionId, String displayName, int playerIndex) {}
```

- [ ] **Step 3: Create LobbyState**

```java
package com.kkhuu131.uno.model;

import java.util.ArrayList;
import java.util.List;

public class LobbyState {

    private final String code;
    private final String hostSessionId;
    private final List<LobbyPlayer> players = new ArrayList<>();
    private LobbyStatus status = LobbyStatus.WAITING;
    private String gameId;

    public LobbyState(String code, String hostSessionId) {
        this.code = code;
        this.hostSessionId = hostSessionId;
    }

    public String getCode() { return code; }

    public String getHostSessionId() { return hostSessionId; }

    public LobbyStatus getStatus() { return status; }

    public String getGameId() { return gameId; }

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

    public synchronized void start(String gameId) {
        this.status = LobbyStatus.IN_PROGRESS;
        this.gameId = gameId;
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./mvnw compile -pl . -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/kkhuu131/uno/model/LobbyStatus.java \
        backend/src/main/java/com/kkhuu131/uno/model/LobbyPlayer.java \
        backend/src/main/java/com/kkhuu131/uno/model/LobbyState.java
git commit -m "feat(model): add LobbyState, LobbyPlayer, and LobbyStatus domain models"
```

---

## Task 4: New Backend DTOs

**Files:** All in `backend/src/main/java/com/kkhuu131/uno/backend/web/dto/`

- [ ] **Step 1: Create all new DTO files**

```java
// PrivateHandUpdate.java
package com.kkhuu131.uno.backend.web.dto;
import java.util.List;
public record PrivateHandUpdate(String gameId, int playerIndex, List<CardView> hand) {}
```

```java
// LobbyPlayerView.java
package com.kkhuu131.uno.backend.web.dto;
public record LobbyPlayerView(int playerIndex, String displayName) {}
```

```java
// LobbySnapshot.java
package com.kkhuu131.uno.backend.web.dto;
import java.util.List;
public record LobbySnapshot(
    String code,
    int hostPlayerIndex,
    List<LobbyPlayerView> players,
    String status,
    String gameId
) {}
```

```java
// CreateLobbyRequest.java
package com.kkhuu131.uno.backend.web.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CreateLobbyRequest(
    @NotBlank @Size(min = 1, max = 24) String displayName
) {}
```

```java
// CreateLobbyResponse.java
package com.kkhuu131.uno.backend.web.dto;
public record CreateLobbyResponse(String code, int playerIndex, LobbySnapshot lobby) {}
```

```java
// JoinLobbyRequest.java
package com.kkhuu131.uno.backend.web.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record JoinLobbyRequest(
    @NotBlank @Size(min = 1, max = 24) String displayName
) {}
```

```java
// JoinLobbyResponse.java
package com.kkhuu131.uno.backend.web.dto;
public record JoinLobbyResponse(int playerIndex, LobbySnapshot lobby) {}
```

```java
// StartGameResponse.java
package com.kkhuu131.uno.backend.web.dto;
public record StartGameResponse(String gameId) {}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw compile -pl . -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/kkhuu131/uno/backend/web/dto/
git commit -m "feat(dto): add lobby and private-hand DTOs"
```

---

## Task 5: Update PlayerStateView + GameSnapshotMapper

**Files:**
- Modify: `backend/src/main/java/com/kkhuu131/uno/backend/web/dto/PlayerStateView.java`
- Modify: `backend/src/main/java/com/kkhuu131/uno/backend/web/GameSnapshotMapper.java`
- Modify: `backend/src/test/java/com/kkhuu131/uno/backend/web/GameSnapshotMapperTest.java` (update constructor calls)

- [ ] **Step 1: Update PlayerStateView**

Replace the entire file:

```java
package com.kkhuu131.uno.backend.web.dto;

import java.util.List;

/**
 * One seat at the table. {@code hand} is {@code null} when the player's cards are redacted
 * (opponents in a real-multiplayer game). {@code handSize} is always accurate.
 */
public record PlayerStateView(String name, List<CardView> hand, int handSize) {}
```

- [ ] **Step 2: Update GameSnapshotMapper**

Replace the entire file:

```java
package com.kkhuu131.uno.backend.web;

import com.kkhuu131.uno.backend.web.dto.CardView;
import com.kkhuu131.uno.backend.web.dto.GameSnapshotResponse;
import com.kkhuu131.uno.backend.web.dto.PlayerStateView;
import com.kkhuu131.uno.model.Card;
import com.kkhuu131.uno.model.Color;
import com.kkhuu131.uno.model.GameState;
import com.kkhuu131.uno.model.Player;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GameSnapshotMapper {

    /** Full snapshot — all hands visible. Used for hot-seat / backward-compatible games. */
    public GameSnapshotResponse toSnapshot(String gameId, GameState state) {
        return buildSnapshot(gameId, state, -2); // -2 = include all hands
    }

    /**
     * Personalized snapshot — {@code callerPlayerIndex}'s hand is populated; all others
     * have {@code hand = null} and only {@code handSize}.
     */
    public GameSnapshotResponse toSnapshot(String gameId, GameState state, int callerPlayerIndex) {
        return buildSnapshot(gameId, state, callerPlayerIndex);
    }

    /** Public snapshot — all hands redacted; only hand sizes are included. */
    public GameSnapshotResponse toPublicSnapshot(String gameId, GameState state) {
        return buildSnapshot(gameId, state, -1); // -1 = no hands
    }

    private GameSnapshotResponse buildSnapshot(String gameId, GameState state, int callerPlayerIndex) {
        Color active = state.getActiveColor();
        String activeColorName = active != null ? active.name() : null;
        List<Player> playerList = state.getPlayers();

        List<PlayerStateView> players = new java.util.ArrayList<>();
        for (int i = 0; i < playerList.size(); i++) {
            Player p = playerList.get(i);
            boolean includeHand = callerPlayerIndex == -2 || callerPlayerIndex == i;
            List<CardView> hand = includeHand
                    ? p.getHand().stream().map(CardView::from).toList()
                    : null;
            players.add(new PlayerStateView(p.getName(), hand, p.getHand().size()));
        }

        List<Card> discard = state.getDiscardPile();
        CardView top = discard.isEmpty() ? null : CardView.from(discard.get(discard.size() - 1));

        String status;
        Integer winnerPlayerIndex = null;
        String winnerName = null;
        if (state.hasWinner()) {
            status = "FINISHED";
            Player winner = state.getWinner();
            if (winner != null) {
                winnerName = winner.getName();
                for (int i = 0; i < playerList.size(); i++) {
                    if (playerList.get(i) == winner) {
                        winnerPlayerIndex = i;
                        break;
                    }
                }
            }
        } else {
            status = "IN_PROGRESS";
        }

        return new GameSnapshotResponse(
                gameId,
                state.getCurrentPlayerIndex(),
                activeColorName,
                players,
                top,
                status,
                winnerPlayerIndex,
                winnerName,
                state.hasPendingDrawStack());
    }
}
```

- [ ] **Step 3: Fix GameSnapshotMapperTest**

Read `backend/src/test/java/com/kkhuu131/uno/backend/web/GameSnapshotMapperTest.java`. Wherever it constructs `PlayerStateView(name, hand)` with two args, add `hand.size()` as the third arg. Wherever it calls `snapshotMapper.toSnapshot(gameId, state)`, leave it unchanged (that overload still works).

- [ ] **Step 4: Run all tests**

Run: `./mvnw test -pl . -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/kkhuu131/uno/backend/web/dto/PlayerStateView.java \
        backend/src/main/java/com/kkhuu131/uno/backend/web/GameSnapshotMapper.java \
        backend/src/test/java/com/kkhuu131/uno/backend/web/GameSnapshotMapperTest.java
git commit -m "feat(mapper): add per-player hand redaction to GameSnapshotMapper"
```

---

## Task 6: LobbySessionService

**Files:**
- Create: `backend/src/main/java/com/kkhuu131/uno/backend/service/LobbySessionService.java`
- Create: `backend/src/test/java/com/kkhuu131/uno/backend/service/LobbySessionServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// backend/src/test/java/com/kkhuu131/uno/backend/service/LobbySessionServiceTest.java
package com.kkhuu131.uno.backend.service;

import com.kkhuu131.uno.backend.exception.LobbyAlreadyStartedException;
import com.kkhuu131.uno.backend.exception.LobbyFullException;
import com.kkhuu131.uno.backend.exception.LobbyNotFoundException;
import com.kkhuu131.uno.model.LobbyState;
import com.kkhuu131.uno.model.LobbyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class LobbySessionServiceTest {

    @Autowired
    private LobbySessionService lobbySessionService;

    @Autowired
    private GameSessionService gameSessionService;

    @Test
    void createLobby_assignsHostAsPlayerZero() {
        LobbyState lobby = lobbySessionService.createLobby("session-host", "Alice");

        assertThat(lobby.getCode()).startsWith("UNO-");
        assertThat(lobby.getPlayers()).hasSize(1);
        assertThat(lobby.getPlayers().get(0).playerIndex()).isEqualTo(0);
        assertThat(lobby.getPlayers().get(0).displayName()).isEqualTo("Alice");
        assertThat(lobby.getHostSessionId()).isEqualTo("session-host");
    }

    @Test
    void joinLobby_addsPlayerAndIncrementsIndex() {
        LobbyState lobby = lobbySessionService.createLobby("s1", "Host");
        String code = lobby.getCode();

        LobbyState after = lobbySessionService.joinLobby(code, "s2", "Guest");

        assertThat(after.getPlayers()).hasSize(2);
        assertThat(after.getPlayers().get(1).playerIndex()).isEqualTo(1);
    }

    @Test
    void joinLobby_unknownCode_throws() {
        assertThatThrownBy(() -> lobbySessionService.joinLobby("UNO-XXXX", "s1", "Bob"))
            .isInstanceOf(LobbyNotFoundException.class);
    }

    @Test
    void joinLobby_alreadyStarted_throws() {
        LobbyState lobby = lobbySessionService.createLobby("s1", "Host");
        lobbySessionService.joinLobby(lobby.getCode(), "s2", "Guest");
        lobbySessionService.startGame(lobby.getCode(), "s1", gameSessionService);

        assertThatThrownBy(() -> lobbySessionService.joinLobby(lobby.getCode(), "s3", "Late"))
            .isInstanceOf(LobbyAlreadyStartedException.class);
    }

    @Test
    void startGame_byNonHost_throws() {
        LobbyState lobby = lobbySessionService.createLobby("host-session", "Host");
        lobbySessionService.joinLobby(lobby.getCode(), "other-session", "Other");

        assertThatThrownBy(() ->
            lobbySessionService.startGame(lobby.getCode(), "other-session", gameSessionService))
            .isInstanceOf(com.kkhuu131.uno.backend.exception.ForbiddenActionException.class);
    }

    @Test
    void startGame_withOnePlayer_throws() {
        LobbyState lobby = lobbySessionService.createLobby("only-host", "Solo");

        assertThatThrownBy(() ->
            lobbySessionService.startGame(lobby.getCode(), "only-host", gameSessionService))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("2");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=LobbySessionServiceTest -q`
Expected: compilation error — `LobbySessionService` not found.

- [ ] **Step 3: Create LobbySessionService**

```java
package com.kkhuu131.uno.backend.service;

import com.kkhuu131.uno.backend.exception.ForbiddenActionException;
import com.kkhuu131.uno.backend.exception.LobbyAlreadyStartedException;
import com.kkhuu131.uno.backend.exception.LobbyFullException;
import com.kkhuu131.uno.backend.exception.LobbyNotFoundException;
import com.kkhuu131.uno.model.LobbyPlayer;
import com.kkhuu131.uno.model.LobbyState;
import com.kkhuu131.uno.model.LobbyStatus;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class LobbySessionService {

    private static final int MAX_PLAYERS = 10;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, LobbyState> lobbies = new ConcurrentHashMap<>();

    public LobbyState createLobby(String sessionId, String displayName) {
        String code = generateUniqueCode();
        LobbyState lobby = new LobbyState(code, sessionId);
        lobby.addPlayer(new LobbyPlayer(sessionId, displayName, 0));
        lobbies.put(code, lobby);
        return lobby;
    }

    public LobbyState joinLobby(String code, String sessionId, String displayName) {
        LobbyState lobby = requireLobby(code);
        if (lobby.getStatus() == LobbyStatus.IN_PROGRESS) {
            throw new LobbyAlreadyStartedException();
        }
        if (lobby.getPlayerCount() >= MAX_PLAYERS) {
            throw new LobbyFullException();
        }
        if (!lobby.hasSession(sessionId)) {
            int nextIndex = lobby.getPlayerCount();
            lobby.addPlayer(new LobbyPlayer(sessionId, displayName, nextIndex));
        }
        return lobby;
    }

    public String startGame(String code, String sessionId, GameSessionService gameSessionService) {
        LobbyState lobby = requireLobby(code);
        if (!lobby.getHostSessionId().equals(sessionId)) {
            throw new ForbiddenActionException();
        }
        if (lobby.getPlayerCount() < 2) {
            throw new IllegalStateException("Need at least 2 players to start");
        }
        if (lobby.getStatus() == LobbyStatus.IN_PROGRESS) {
            return lobby.getGameId();
        }
        String gameId = gameSessionService.createGame(lobby.getPlayers());
        lobby.start(gameId);
        return gameId;
    }

    public Optional<LobbyState> findLobby(String code) {
        return Optional.ofNullable(lobbies.get(code));
    }

    private LobbyState requireLobby(String code) {
        LobbyState lobby = lobbies.get(code);
        if (lobby == null) throw new LobbyNotFoundException(code);
        return lobby;
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = "UNO-" + RANDOM.ints(4, 0, CODE_CHARS.length())
                    .mapToObj(i -> String.valueOf(CODE_CHARS.charAt(i)))
                    .collect(Collectors.joining());
        } while (lobbies.containsKey(code));
        return code;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=LobbySessionServiceTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Run full test suite**

Run: `./mvnw test -pl . -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/kkhuu131/uno/backend/service/LobbySessionService.java \
        backend/src/test/java/com/kkhuu131/uno/backend/service/LobbySessionServiceTest.java
git commit -m "feat(service): add LobbySessionService with create/join/start"
```

---

## Task 7: Update GameSessionService

**Files:**
- Modify: `backend/src/main/java/com/kkhuu131/uno/backend/service/GameSessionService.java`

- [ ] **Step 1: Add session maps and new createGame overload**

Add these fields after the `games` field:

```java
private final Map<String, Map<String, Integer>> sessionMaps = new ConcurrentHashMap<>();
```

Add this new method after the existing `createGame()`:

```java
/**
 * Creates a game from a lobby's player list and records the sessionId↔playerIndex mapping
 * so mutation endpoints can validate callers.
 */
public String createGame(List<LobbyPlayer> lobbyPlayers) {
    String id = UUID.randomUUID().toString();
    GameState state = new GameState();
    state.initializeGame(lobbyPlayers.stream().map(LobbyPlayer::displayName).toList());
    games.put(id, state);

    Map<String, Integer> sessionMap = new ConcurrentHashMap<>();
    for (LobbyPlayer p : lobbyPlayers) {
        sessionMap.put(p.sessionId(), p.playerIndex());
    }
    sessionMaps.put(id, sessionMap);
    return id;
}

/** Returns the playerIndex for the given sessionId in this game, or empty if not found. */
public Optional<Integer> getPlayerIndex(String gameId, String sessionId) {
    Map<String, Integer> map = sessionMaps.get(gameId);
    if (map == null) return Optional.empty();
    return Optional.ofNullable(map.get(sessionId));
}

/** Returns the full sessionId→playerIndex map (empty map if game has no session mappings). */
public Map<String, Integer> getSessionMap(String gameId) {
    return sessionMaps.getOrDefault(gameId, Map.of());
}
```

Add the required import: `import com.kkhuu131.uno.model.LobbyPlayer;`

- [ ] **Step 2: Run full test suite**

Run: `./mvnw test -pl . -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/kkhuu131/uno/backend/service/GameSessionService.java
git commit -m "feat(service): add session-mapped createGame and playerIndex lookup"
```

---

## Task 8: WebSocketConfig + Broadcast Services

**Files:**
- Create: `backend/src/main/java/com/kkhuu131/uno/backend/config/WebSocketConfig.java`
- Create: `backend/src/main/java/com/kkhuu131/uno/backend/service/GameBroadcastService.java`
- Create: `backend/src/main/java/com/kkhuu131/uno/backend/service/LobbyBroadcastService.java`
- Modify: `backend/src/main/java/com/kkhuu131/uno/backend/config/WebConfig.java` (allow X-Session-Id header)

- [ ] **Step 1: Create WebSocketConfig**

```java
package com.kkhuu131.uno.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;

    public WebSocketConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
```

- [ ] **Step 2: Create GameBroadcastService**

```java
package com.kkhuu131.uno.backend.service;

import com.kkhuu131.uno.backend.web.GameSnapshotMapper;
import com.kkhuu131.uno.backend.web.dto.CardView;
import com.kkhuu131.uno.backend.web.dto.PrivateHandUpdate;
import com.kkhuu131.uno.model.GameState;
import java.util.List;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class GameBroadcastService {

    private final SimpMessagingTemplate messaging;
    private final GameSnapshotMapper mapper;

    public GameBroadcastService(SimpMessagingTemplate messaging, GameSnapshotMapper mapper) {
        this.messaging = messaging;
        this.mapper = mapper;
    }

    /**
     * Pushes a public (hand-redacted) snapshot to all subscribers of this game, then pushes
     * each player's private hand to their personal topic.
     */
    public void broadcastUpdate(String gameId, GameState state, Map<String, Integer> sessionMap) {
        messaging.convertAndSend("/topic/games/" + gameId, mapper.toPublicSnapshot(gameId, state));
        sessionMap.forEach((sessionId, playerIndex) -> {
            List<CardView> hand = state.getPlayers().get(playerIndex).getHand()
                    .stream().map(CardView::from).toList();
            messaging.convertAndSend(
                    "/topic/games/" + gameId + "/private/" + sessionId,
                    new PrivateHandUpdate(gameId, playerIndex, hand));
        });
    }
}
```

- [ ] **Step 3: Create LobbyBroadcastService**

```java
package com.kkhuu131.uno.backend.service;

import com.kkhuu131.uno.backend.web.LobbySnapshotMapper;
import com.kkhuu131.uno.model.LobbyState;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class LobbyBroadcastService {

    private final SimpMessagingTemplate messaging;
    private final LobbySnapshotMapper mapper;

    public LobbyBroadcastService(SimpMessagingTemplate messaging, LobbySnapshotMapper mapper) {
        this.messaging = messaging;
        this.mapper = mapper;
    }

    public void broadcastUpdate(LobbyState lobby) {
        messaging.convertAndSend("/topic/lobbies/" + lobby.getCode(), mapper.toSnapshot(lobby));
    }
}
```

- [ ] **Step 4: Create LobbySnapshotMapper**

Create `backend/src/main/java/com/kkhuu131/uno/backend/web/LobbySnapshotMapper.java`:

```java
package com.kkhuu131.uno.backend.web;

import com.kkhuu131.uno.backend.web.dto.LobbyPlayerView;
import com.kkhuu131.uno.backend.web.dto.LobbySnapshot;
import com.kkhuu131.uno.model.LobbyPlayer;
import com.kkhuu131.uno.model.LobbyState;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LobbySnapshotMapper {

    public LobbySnapshot toSnapshot(LobbyState lobby) {
        List<LobbyPlayerView> playerViews = lobby.getPlayers().stream()
                .map(p -> new LobbyPlayerView(p.playerIndex(), p.displayName()))
                .toList();
        int hostPlayerIndex = lobby.getPlayers().stream()
                .filter(p -> p.sessionId().equals(lobby.getHostSessionId()))
                .findFirst()
                .map(LobbyPlayer::playerIndex)
                .orElse(0);
        return new LobbySnapshot(
                lobby.getCode(),
                hostPlayerIndex,
                playerViews,
                lobby.getStatus().name(),
                lobby.getGameId());
    }
}
```

- [ ] **Step 5: Allow X-Session-Id header in WebConfig CORS**

In `WebConfig.java`, update `allowedHeaders` to explicitly include `X-Session-Id`:

```java
registry.addMapping("/api/**")
        .allowedOrigins(origins.toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*");
```

`allowedHeaders("*")` already covers `X-Session-Id`, so no change is needed — but verify it.

- [ ] **Step 6: Verify it compiles**

Run: `./mvnw compile -pl . -q`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Run full test suite**

Run: `./mvnw test -pl . -q`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/kkhuu131/uno/backend/config/WebSocketConfig.java \
        backend/src/main/java/com/kkhuu131/uno/backend/service/GameBroadcastService.java \
        backend/src/main/java/com/kkhuu131/uno/backend/service/LobbyBroadcastService.java \
        backend/src/main/java/com/kkhuu131/uno/backend/web/LobbySnapshotMapper.java
git commit -m "feat(websocket): add STOMP config and broadcast services"
```

---

## Task 9: LobbyController

**Files:**
- Create: `backend/src/main/java/com/kkhuu131/uno/backend/web/LobbyController.java`

- [ ] **Step 1: Create LobbyController**

```java
package com.kkhuu131.uno.backend.web;

import com.kkhuu131.uno.backend.service.GameSessionService;
import com.kkhuu131.uno.backend.service.LobbyBroadcastService;
import com.kkhuu131.uno.backend.service.LobbySessionService;
import com.kkhuu131.uno.backend.web.dto.CreateLobbyRequest;
import com.kkhuu131.uno.backend.web.dto.CreateLobbyResponse;
import com.kkhuu131.uno.backend.web.dto.JoinLobbyRequest;
import com.kkhuu131.uno.backend.web.dto.JoinLobbyResponse;
import com.kkhuu131.uno.backend.web.dto.LobbySnapshot;
import com.kkhuu131.uno.backend.web.dto.StartGameResponse;
import com.kkhuu131.uno.model.LobbyState;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lobbies")
public class LobbyController {

    private final LobbySessionService lobbySessionService;
    private final GameSessionService gameSessionService;
    private final LobbySnapshotMapper snapshotMapper;
    private final LobbyBroadcastService broadcastService;

    public LobbyController(
            LobbySessionService lobbySessionService,
            GameSessionService gameSessionService,
            LobbySnapshotMapper snapshotMapper,
            LobbyBroadcastService broadcastService) {
        this.lobbySessionService = lobbySessionService;
        this.gameSessionService = gameSessionService;
        this.snapshotMapper = snapshotMapper;
        this.broadcastService = broadcastService;
    }

    @PostMapping
    public ResponseEntity<CreateLobbyResponse> createLobby(
            @RequestHeader("X-Session-Id") String sessionId,
            @Valid @RequestBody CreateLobbyRequest body) {
        LobbyState lobby = lobbySessionService.createLobby(sessionId, body.displayName());
        LobbySnapshot snapshot = snapshotMapper.toSnapshot(lobby);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateLobbyResponse(lobby.getCode(), 0, snapshot));
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<JoinLobbyResponse> joinLobby(
            @PathVariable String code,
            @RequestHeader("X-Session-Id") String sessionId,
            @Valid @RequestBody JoinLobbyRequest body) {
        LobbyState lobby = lobbySessionService.joinLobby(code, sessionId, body.displayName());
        int playerIndex = lobby.getPlayers().stream()
                .filter(p -> p.sessionId().equals(sessionId))
                .findFirst()
                .orElseThrow()
                .playerIndex();
        LobbySnapshot snapshot = snapshotMapper.toSnapshot(lobby);
        broadcastService.broadcastUpdate(lobby);
        return ResponseEntity.ok(new JoinLobbyResponse(playerIndex, snapshot));
    }

    @PostMapping("/{code}/start")
    public ResponseEntity<StartGameResponse> startGame(
            @PathVariable String code,
            @RequestHeader("X-Session-Id") String sessionId) {
        String gameId = lobbySessionService.startGame(code, sessionId, gameSessionService);
        LobbyState lobby = lobbySessionService.findLobby(code).orElseThrow();
        broadcastService.broadcastUpdate(lobby);
        return ResponseEntity.ok(new StartGameResponse(gameId));
    }

    @GetMapping("/{code}")
    public ResponseEntity<LobbySnapshot> getLobby(@PathVariable String code) {
        return lobbySessionService.findLobby(code)
                .map(lobby -> ResponseEntity.ok(snapshotMapper.toSnapshot(lobby)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

- [ ] **Step 2: Verify it compiles and all tests pass**

Run: `./mvnw test -pl . -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/kkhuu131/uno/backend/web/LobbyController.java
git commit -m "feat(controller): add LobbyController with create/join/start/get"
```

---

## Task 10: Update GameController

**Files:**
- Modify: `backend/src/main/java/com/kkhuu131/uno/backend/web/GameController.java`

- [ ] **Step 1: Update GameController**

Replace the entire file:

```java
package com.kkhuu131.uno.backend.web;

import com.kkhuu131.uno.backend.exception.ForbiddenActionException;
import com.kkhuu131.uno.backend.service.GameBroadcastService;
import com.kkhuu131.uno.backend.service.GameSessionService;
import com.kkhuu131.uno.backend.web.dto.CardView;
import com.kkhuu131.uno.backend.web.dto.DrawCardRequest;
import com.kkhuu131.uno.backend.web.dto.DrawCardResponse;
import com.kkhuu131.uno.backend.web.dto.GameCreatedResponse;
import com.kkhuu131.uno.backend.web.dto.GameSnapshotResponse;
import com.kkhuu131.uno.backend.web.dto.PassTurnRequest;
import com.kkhuu131.uno.backend.web.dto.PlayCardRequest;
import com.kkhuu131.uno.model.GameState;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GameController {

    private final GameSessionService gameSessionService;
    private final GameSnapshotMapper snapshotMapper;
    private final GameBroadcastService broadcastService;

    public GameController(
            GameSessionService gameSessionService,
            GameSnapshotMapper snapshotMapper,
            GameBroadcastService broadcastService) {
        this.gameSessionService = gameSessionService;
        this.snapshotMapper = snapshotMapper;
        this.broadcastService = broadcastService;
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello from UNO backend";
    }

    @PostMapping("/games")
    public ResponseEntity<GameCreatedResponse> createGame() {
        String id = gameSessionService.createGame();
        return ResponseEntity.status(HttpStatus.CREATED).body(new GameCreatedResponse(id));
    }

    /**
     * Returns a personalized snapshot when X-Session-Id is provided for a lobby-created game.
     * Falls back to a full snapshot for hot-seat games (no session mappings).
     */
    @GetMapping("/games/{gameId}")
    public ResponseEntity<GameSnapshotResponse> getGame(
            @PathVariable String gameId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        return gameSessionService.findGame(gameId)
                .map(state -> {
                    Map<String, Integer> sessionMap = gameSessionService.getSessionMap(gameId);
                    GameSnapshotResponse snap;
                    if (sessionId != null && !sessionMap.isEmpty()) {
                        int callerIdx = sessionMap.getOrDefault(sessionId, -1);
                        snap = snapshotMapper.toSnapshot(gameId, state, callerIdx);
                    } else {
                        snap = snapshotMapper.toSnapshot(gameId, state);
                    }
                    return ResponseEntity.ok(snap);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/games/{gameId}/draw")
    public ResponseEntity<?> drawCard(
            @PathVariable String gameId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @Valid @RequestBody DrawCardRequest body) {
        validateCaller(gameId, sessionId, body.playerIndex());
        return gameSessionService
                .drawCard(gameId, body)
                .<ResponseEntity<?>>map(outcome -> {
                    broadcastService.broadcastUpdate(
                            gameId, outcome.state(), gameSessionService.getSessionMap(gameId));
                    return ResponseEntity.ok(new DrawCardResponse(
                            CardView.from(outcome.drawnCard()),
                            snapshotMapper.toSnapshot(gameId, outcome.state(), body.playerIndex())));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/games/{gameId}/pass")
    public ResponseEntity<?> passTurn(
            @PathVariable String gameId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @Valid @RequestBody PassTurnRequest body) {
        validateCaller(gameId, sessionId, body.playerIndex());
        return gameSessionService
                .passTurn(gameId, body)
                .<ResponseEntity<?>>map(state -> {
                    broadcastService.broadcastUpdate(
                            gameId, state, gameSessionService.getSessionMap(gameId));
                    return ResponseEntity.ok(snapshotMapper.toSnapshot(gameId, state, body.playerIndex()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/games/{gameId}/play")
    public ResponseEntity<?> playCard(
            @PathVariable String gameId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @Valid @RequestBody PlayCardRequest body) {
        validateCaller(gameId, sessionId, body.playerIndex());
        return gameSessionService
                .playCard(gameId, body)
                .<ResponseEntity<?>>map(state -> {
                    broadcastService.broadcastUpdate(
                            gameId, state, gameSessionService.getSessionMap(gameId));
                    return ResponseEntity.ok(snapshotMapper.toSnapshot(gameId, state, body.playerIndex()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * If the game was created via lobby (has session mappings), ensures the caller's sessionId
     * matches the claimed playerIndex. Hot-seat games (no mappings) skip this check.
     */
    private void validateCaller(String gameId, String sessionId, int claimedPlayerIndex) {
        Map<String, Integer> sessionMap = gameSessionService.getSessionMap(gameId);
        if (sessionMap.isEmpty()) return; // hot-seat game, no validation needed
        if (sessionId == null || !Integer.valueOf(claimedPlayerIndex).equals(sessionMap.get(sessionId))) {
            throw new ForbiddenActionException();
        }
    }
}
```

- [ ] **Step 2: Run full test suite**

Run: `./mvnw test -pl . -q`
Expected: BUILD SUCCESS. The existing `GameControllerTest` may need updating if it checks constructor arg count — fix those if they fail.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/kkhuu131/uno/backend/web/GameController.java
git commit -m "feat(controller): add sessionId validation and broadcast to GameController"
```

---

## Task 11: Frontend Dependencies + Types + Session Utils + API Client

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/types/index.ts`
- Create: `frontend/src/utils/session.ts`
- Modify: `frontend/src/api/client.ts`

- [ ] **Step 1: Install frontend dependencies**

Run from `frontend/`:
```bash
npm install @stomp/stompjs sockjs-client react-router-dom
npm install -D @types/sockjs-client @types/react-router-dom
```

Expected: `package.json` updated, no errors.

- [ ] **Step 2: Update TypeScript types**

Replace the entire `frontend/src/types/index.ts`:

```typescript
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
```

- [ ] **Step 3: Create session utility**

```typescript
// frontend/src/utils/session.ts
const SESSION_ID_KEY = 'uno_session_id'
const DISPLAY_NAME_KEY = 'uno_display_name'

export function getSessionId(): string {
  let id = localStorage.getItem(SESSION_ID_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(SESSION_ID_KEY, id)
  }
  return id
}

export function getDisplayName(): string {
  return localStorage.getItem(DISPLAY_NAME_KEY) ?? ''
}

export function setDisplayName(name: string): void {
  localStorage.setItem(DISPLAY_NAME_KEY, name.trim())
}
```

- [ ] **Step 4: Update API client**

Replace the entire `frontend/src/api/client.ts`:

```typescript
import type { DrawCardResponse, GameSnapshot, LobbySnapshot } from '../types'
import { getSessionId } from '../utils/session'

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? 'http://localhost:8080/api'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Session-Id': getSessionId(),
      ...(options?.headers ?? {}),
    },
    ...options,
  })
  if (!res.ok) {
    let message = res.statusText
    try {
      const body = await res.json() as { message?: string }
      if (body.message) message = body.message
    } catch {
      // leave statusText
    }
    throw new Error(message)
  }
  return res.json() as Promise<T>
}

export const api = {
  // ── Game (existing) ──────────────────────────────────────────────────
  createGame: () =>
    request<{ gameId: string }>('/games', { method: 'POST' }),

  getGame: (id: string) =>
    request<GameSnapshot>(`/games/${id}`),

  playCard: (id: string, playerIndex: number, handIndex: number, chosenColor?: string) =>
    request<GameSnapshot>(`/games/${id}/play`, {
      method: 'POST',
      body: JSON.stringify({ playerIndex, handIndex, chosenColor: chosenColor ?? null }),
    }),

  drawCard: (id: string, playerIndex: number, endTurn?: boolean) =>
    request<DrawCardResponse>(`/games/${id}/draw`, {
      method: 'POST',
      body: JSON.stringify({ playerIndex, endTurn: endTurn ?? null }),
    }),

  passTurn: (id: string, playerIndex: number) =>
    request<GameSnapshot>(`/games/${id}/pass`, {
      method: 'POST',
      body: JSON.stringify({ playerIndex }),
    }),

  // ── Lobby (new) ──────────────────────────────────────────────────────
  createLobby: (displayName: string) =>
    request<{ code: string; playerIndex: number; lobby: LobbySnapshot }>('/lobbies', {
      method: 'POST',
      body: JSON.stringify({ displayName }),
    }),

  joinLobby: (code: string, displayName: string) =>
    request<{ playerIndex: number; lobby: LobbySnapshot }>(`/lobbies/${code}/join`, {
      method: 'POST',
      body: JSON.stringify({ displayName }),
    }),

  startGame: (code: string) =>
    request<{ gameId: string }>(`/lobbies/${code}/start`, { method: 'POST' }),

  getLobby: (code: string) =>
    request<LobbySnapshot>(`/lobbies/${code}`),
}
```

- [ ] **Step 5: Verify TypeScript compiles**

Run from `frontend/`: `npm run build`
Expected: no type errors. Fix any that arise from the `PlayerStateView.hand` now being nullable (callers that assumed non-null will need `?? []`).

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/package-lock.json \
        frontend/src/types/index.ts \
        frontend/src/utils/session.ts \
        frontend/src/api/client.ts
git commit -m "feat(frontend): add deps, types, session utils, and lobby API client"
```

---

## Task 12: useGame Refactor with STOMP

**Files:**
- Create: `frontend/src/hooks/useStompClient.ts`
- Modify: `frontend/src/hooks/useGame.ts`

- [ ] **Step 1: Create useStompClient**

```typescript
// frontend/src/hooks/useStompClient.ts
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useEffect, useRef, useState } from 'react'

const WS_BASE =
  (import.meta.env.VITE_WS_BASE as string | undefined) ?? 'http://localhost:8080/ws'

export interface StompSubscription {
  topic: string
  onMessage: (body: unknown) => void
}

export function useStompClient(subscriptions: StompSubscription[]) {
  const [connected, setConnected] = useState(false)
  // Store subscriptions in a ref so the effect does not re-run on every render
  const subscriptionsRef = useRef(subscriptions)
  subscriptionsRef.current = subscriptions

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_BASE) as WebSocket,
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        subscriptionsRef.current.forEach(({ topic, onMessage }) => {
          client.subscribe(topic, (msg) => {
            try {
              onMessage(JSON.parse(msg.body))
            } catch {
              // ignore malformed frames
            }
          })
        })
      },
      onDisconnect: () => setConnected(false),
    })
    client.activate()
    return () => {
      client.deactivate()
    }
  }, []) // connect once per mount; subscriptions are read from ref

  return { connected }
}
```

- [ ] **Step 2: Refactor useGame**

Replace the entire `frontend/src/hooks/useGame.ts`:

```typescript
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/client'
import type { CardView, GameSnapshot, PrivateHandUpdate } from '../types'
import { getSessionId } from '../utils/session'
import { useStompClient } from './useStompClient'

export function useGame(gameId: string, localPlayerIndex: number) {
  const [publicSnapshot, setPublicSnapshot] = useState<GameSnapshot | null>(null)
  const [privateHand, setPrivateHand] = useState<CardView[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const sessionId = getSessionId()

  // Initial load via REST so the table is not blank before WS connects
  useEffect(() => {
    api.getGame(gameId).then(setPublicSnapshot).catch(() => {})
  }, [gameId])

  // STOMP subscriptions
  useStompClient([
    {
      topic: `/topic/games/${gameId}`,
      onMessage: (body) => setPublicSnapshot(body as GameSnapshot),
    },
    {
      topic: `/topic/games/${gameId}/private/${sessionId}`,
      onMessage: (body) => {
        const update = body as PrivateHandUpdate
        setPrivateHand(update.hand)
      },
    },
  ])

  // Merge: local player gets the private hand; opponents keep their redacted state
  const snapshot = useMemo((): GameSnapshot | null => {
    if (!publicSnapshot) return null
    return {
      ...publicSnapshot,
      players: publicSnapshot.players.map((p, i) =>
        i === localPlayerIndex
          ? { ...p, hand: privateHand ?? [] }
          : { ...p, hand: p.hand ?? [] },
      ),
    }
  }, [publicSnapshot, privateHand, localPlayerIndex])

  const act = useCallback(
    async (fn: () => Promise<GameSnapshot | { game: GameSnapshot }>) => {
      if (busy) return
      setBusy(true)
      setError(null)
      try {
        const result = await fn()
        const snap = 'game' in result ? result.game : result
        // Update local player's hand from the REST response immediately
        setPublicSnapshot(snap)
        if (snap.players[localPlayerIndex].hand) {
          setPrivateHand(snap.players[localPlayerIndex].hand)
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Something went wrong')
      } finally {
        setBusy(false)
      }
    },
    [busy, localPlayerIndex],
  )

  const playCard = (playerIndex: number, handIndex: number, chosenColor?: string) =>
    act(() => api.playCard(gameId, playerIndex, handIndex, chosenColor))

  const drawCard = (playerIndex: number, endTurn?: boolean) =>
    act(() => api.drawCard(gameId, playerIndex, endTurn))

  const passTurn = (playerIndex: number) =>
    act(() => api.passTurn(gameId, playerIndex))

  return {
    snapshot,
    error,
    busy,
    clearError: () => setError(null),
    playCard,
    drawCard,
    passTurn,
  }
}
```

- [ ] **Step 3: Verify TypeScript compiles**

Run from `frontend/`: `npm run build`
Expected: no type errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/hooks/useStompClient.ts frontend/src/hooks/useGame.ts
git commit -m "feat(hooks): replace polling with STOMP in useGame; add useStompClient"
```

---

## Task 13: UsernameBar + Home Screen

**Files:**
- Create: `frontend/src/components/UsernameBar.tsx`
- Create: `frontend/src/components/Home.tsx`

- [ ] **Step 1: Create UsernameBar**

```tsx
// frontend/src/components/UsernameBar.tsx
import { useEffect, useRef, useState } from 'react'
import { getDisplayName, setDisplayName } from '../utils/session'

interface Props {
  onChange?: (name: string) => void
}

export function UsernameBar({ onChange }: Props) {
  const [name, setName] = useState(getDisplayName)
  const [editing, setEditing] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editing) inputRef.current?.select()
  }, [editing])

  function commit() {
    const trimmed = name.trim()
    if (trimmed) {
      setDisplayName(trimmed)
      setName(trimmed)
      onChange?.(trimmed)
    } else {
      setName(getDisplayName())
    }
    setEditing(false)
  }

  return (
    <div className="username-bar">
      {editing ? (
        <input
          ref={inputRef}
          className="username-bar__input"
          value={name}
          maxLength={24}
          onChange={(e) => setName(e.target.value)}
          onBlur={commit}
          onKeyDown={(e) => {
            if (e.key === 'Enter') commit()
            if (e.key === 'Escape') { setName(getDisplayName()); setEditing(false) }
          }}
        />
      ) : (
        <span className="username-bar__display">
          {name || <span className="username-bar__placeholder">Set your name</span>}
          <button className="username-bar__edit" onClick={() => setEditing(true)} aria-label="Edit name">
            ✎
          </button>
        </span>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Create Home screen**

```tsx
// frontend/src/components/Home.tsx
import { motion } from 'framer-motion'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { getDisplayName, setDisplayName } from '../utils/session'
import { UsernameBar } from './UsernameBar'

export function Home() {
  const navigate = useNavigate()
  const [joinCode, setJoinCode] = useState('')
  const [loading, setLoading] = useState<'create' | 'join' | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleCreate() {
    const name = getDisplayName()
    if (!name) { setError('Set your name first'); return }
    setLoading('create')
    setError(null)
    try {
      const { code, playerIndex } = await api.createLobby(name)
      sessionStorage.setItem('uno_player_index', String(playerIndex))
      navigate('/lobby/' + code)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create lobby')
      setLoading(null)
    }
  }

  async function handleJoin() {
    const name = getDisplayName()
    if (!name) { setError('Set your name first'); return }
    const code = joinCode.trim().toUpperCase()
    if (!code) { setError('Enter a lobby code'); return }
    setLoading('join')
    setError(null)
    try {
      const { playerIndex } = await api.joinLobby(code, name)
      sessionStorage.setItem('uno_player_index', String(playerIndex))
      navigate('/lobby/' + code)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to join lobby')
      setLoading(null)
    }
  }

  return (
    <div className="setup">
      <UsernameBar onChange={() => setDisplayName(getDisplayName())} />

      <motion.div
        className="setup__card"
        initial={{ scale: 0.8, opacity: 0, y: 24 }}
        animate={{ scale: 1, opacity: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 320, damping: 24 }}
      >
        <motion.div
          className="setup__logo"
          initial={{ scale: 0.5, rotate: -12 }}
          animate={{ scale: 1, rotate: 0 }}
          transition={{ type: 'spring', stiffness: 260, damping: 18, delay: 0.1 }}
        >
          UNO
        </motion.div>

        {error && (
          <motion.p className="setup__error" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
            {error}
          </motion.p>
        )}

        <motion.button
          className="btn btn--start"
          onClick={handleCreate}
          disabled={!!loading}
          whileHover={{ scale: 1.03 }}
          whileTap={{ scale: 0.97 }}
        >
          {loading === 'create' ? 'Creating…' : '+ Create Game'}
        </motion.button>

        <div className="setup__divider" />

        <div className="setup__join">
          <input
            className="setup__join-input"
            placeholder="Lobby code (e.g. UNO-7X3K)"
            value={joinCode}
            maxLength={8}
            onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
            onKeyDown={(e) => { if (e.key === 'Enter') handleJoin() }}
          />
          <motion.button
            className="btn btn--join"
            onClick={handleJoin}
            disabled={!!loading}
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.97 }}
          >
            {loading === 'join' ? 'Joining…' : 'Join Game'}
          </motion.button>
        </div>
      </motion.div>
    </div>
  )
}
```

- [ ] **Step 3: Verify TypeScript compiles**

Run from `frontend/`: `npm run build`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/UsernameBar.tsx frontend/src/components/Home.tsx
git commit -m "feat(frontend): add Home screen and UsernameBar"
```

---

## Task 14: LobbyRoom Component

**Files:**
- Create: `frontend/src/components/LobbyRoom.tsx`

- [ ] **Step 1: Create LobbyRoom**

```tsx
// frontend/src/components/LobbyRoom.tsx
import { motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { LobbySnapshot } from '../types'
import { getSessionId } from '../utils/session'
import { useStompClient } from '../hooks/useStompClient'
import { UsernameBar } from './UsernameBar'

export function LobbyRoom() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const [lobby, setLobby] = useState<LobbySnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const [copied, setCopied] = useState(false)

  const sessionId = getSessionId()

  // Initial REST load
  useEffect(() => {
    if (!code) return
    api.getLobby(code)
      .then(setLobby)
      .catch(() => setError('Lobby not found'))
  }, [code])

  // Live updates via STOMP
  useStompClient(
    code
      ? [{ topic: `/topic/lobbies/${code}`, onMessage: (body) => {
          const snap = body as LobbySnapshot
          setLobby(snap)
          if (snap.status === 'IN_PROGRESS' && snap.gameId) {
            navigate('/game/' + snap.gameId, {
              state: {
                playerIndex: snap.players.find(
                  (p) => p.playerIndex === parseInt(sessionStorage.getItem('uno_player_index') ?? '-1')
                )?.playerIndex ?? 0,
              },
            })
          }
        }}]
      : []
  )

  function copyCode() {
    if (!code) return
    navigator.clipboard.writeText(code).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  async function handleStart() {
    if (!code) return
    setStarting(true)
    setError(null)
    try {
      const { gameId } = await api.startGame(code)
      const playerIndex = parseInt(sessionStorage.getItem('uno_player_index') ?? '0')
      navigate('/game/' + gameId, { state: { playerIndex } })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to start game')
      setStarting(false)
    }
  }

  if (!lobby) {
    return <div className="lobby-loading">{error ?? 'Loading lobby…'}</div>
  }

  const isHost = lobby.hostPlayerIndex ===
    lobby.players.find(
      (p) => p.playerIndex === parseInt(sessionStorage.getItem('uno_player_index') ?? '-1')
    )?.playerIndex

  return (
    <div className="lobby">
      <UsernameBar />

      <motion.div
        className="lobby__card"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <h1 className="lobby__title">Waiting for players…</h1>

        <div className="lobby__code-row">
          <span className="lobby__code">{code}</span>
          <button className="btn btn--copy" onClick={copyCode}>
            {copied ? 'Copied!' : 'Copy'}
          </button>
        </div>

        <p className="lobby__hint">Share this code with friends to invite them</p>

        <ul className="lobby__players">
          {lobby.players.map((p) => (
            <motion.li
              key={p.playerIndex}
              className="lobby__player"
              initial={{ opacity: 0, x: -12 }}
              animate={{ opacity: 1, x: 0 }}
            >
              {p.playerIndex === lobby.hostPlayerIndex && (
                <span className="lobby__host-crown">♛</span>
              )}
              {p.displayName}
            </motion.li>
          ))}
        </ul>

        {error && <p className="lobby__error">{error}</p>}

        {isHost && (
          <motion.button
            className="btn btn--start"
            onClick={handleStart}
            disabled={starting || lobby.players.length < 2}
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.97 }}
          >
            {starting
              ? 'Starting…'
              : lobby.players.length < 2
              ? 'Waiting for players…'
              : `Start Game (${lobby.players.length} players)`}
          </motion.button>
        )}

        {!isHost && (
          <p className="lobby__waiting">Waiting for the host to start the game…</p>
        )}
      </motion.div>
    </div>
  )
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `npm run build`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/LobbyRoom.tsx
git commit -m "feat(frontend): add LobbyRoom waiting room with live player list"
```

---

## Task 15: CardBack + OpponentSlot

**Files:**
- Modify: `frontend/src/components/Card.tsx`
- Create: `frontend/src/components/OpponentSlot.tsx`

- [ ] **Step 1: Add CardBack to Card.tsx**

Add this export at the end of `frontend/src/components/Card.tsx` (keep everything existing):

```tsx
import { getDeckImageSrc } from '../utils/cardImage'

interface CardBackProps {
  index?: number
  total?: number
  style?: React.CSSProperties
}

export function CardBack({ index = 0, total = 1, style }: CardBackProps) {
  const spread = Math.min(total, 7)
  const rotation = spread > 1 ? ((index - (spread - 1) / 2) * 9) : 0
  const xOffset = spread > 1 ? ((index - (spread - 1) / 2) * 14) : 0
  return (
    <img
      src={getDeckImageSrc()}
      className="card-back"
      alt="Card back"
      draggable={false}
      style={{
        transform: `rotate(${rotation}deg) translateX(${xOffset}px)`,
        ...style,
      }}
    />
  )
}
```

Note: `getDeckImageSrc` is already imported at the top of `Card.tsx`. Add the import for `CardBackProps` and `CardBack` export only — do NOT duplicate the existing `getDeckImageSrc` import.

- [ ] **Step 2: Create OpponentSlot**

```tsx
// frontend/src/components/OpponentSlot.tsx
import type { PlayerStateView } from '../types'
import { CardBack } from './Card'

interface Props {
  player: PlayerStateView
  isActive: boolean
  style: React.CSSProperties
}

const CARD_W = 52
const CARD_H = 74

export function OpponentSlot({ player, isActive, style }: Props) {
  const displayCount = Math.min(player.handSize, 7)
  const fanWidth = displayCount > 1 ? (CARD_W * 0.55) : 0
  const containerWidth = displayCount > 0 ? fanWidth * (displayCount - 1) + CARD_W : CARD_W

  return (
    <div
      className={`opponent-slot${isActive ? ' opponent-slot--active' : ''}`}
      style={style}
    >
      <div className="opponent-slot__name">{player.name}</div>

      <div className="opponent-slot__fan" style={{ width: containerWidth, height: CARD_H }}>
        {Array.from({ length: displayCount }).map((_, i) => (
          <CardBack
            key={i}
            index={i}
            total={displayCount}
            style={{ position: 'absolute', left: i * fanWidth, width: CARD_W, height: CARD_H }}
          />
        ))}
      </div>

      <div className="opponent-slot__badge">{player.handSize}</div>
    </div>
  )
}
```

- [ ] **Step 3: Verify TypeScript compiles**

Run: `npm run build`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/Card.tsx frontend/src/components/OpponentSlot.tsx
git commit -m "feat(frontend): add CardBack and OpponentSlot components"
```

---

## Task 16: GameTable Circular Layout Refactor

**Files:**
- Modify: `frontend/src/components/GameTable.tsx`

- [ ] **Step 1: Replace GameTable.tsx**

Replace the entire file:

```tsx
import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useGame } from '../hooks/useGame'
import type { CardColor, CardView } from '../types'
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
import { UsernameBar } from './UsernameBar'
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

interface Props {
  gameId: string
  localPlayerIndex: number
}

export function GameTable({ gameId, localPlayerIndex }: Props) {
  const navigate = useNavigate()
  const { snapshot, error, busy, clearError, playCard, drawCard, passTurn } = useGame(
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

  async function handleDraw() {
    const deckRect = deckRef.current?.getBoundingClientRect()
    const handRect = localHandRef.current?.getBoundingClientRect()
    if (deckRect && handRect) {
      const id = `draw-${Date.now()}`
      const toX = Math.min(handRect.right - CARD_W - 8, handRect.left + handRect.width * 0.7)
      const toY = handRect.top + (handRect.height - CARD_H) / 2
      addFlyingCard({
        id,
        card: null,
        from: { x: deckRect.left, y: deckRect.top, w: deckRect.width, h: deckRect.height },
        to: { x: toX, y: toY, w: CARD_W, h: CARD_H },
        onComplete: () => removeFlyingCard(id),
      })
    }
    await drawCard(localPlayerIndex, false)
    setHasDrawnThisTurn(true)
  }

  async function handlePass() {
    await passTurn(localPlayerIndex)
    setHasDrawnThisTurn(false)
  }

  return (
    <div className="table">
      <UsernameBar />
      <TurnBanner visible={bannerVisible} name={bannerName} />

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
            className="deck-pile"
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
        <GameOver winnerName={winnerName} onNewGame={() => navigate('/')} />
      )}

      {error && <Toast message={error} onDismiss={clearError} />}
    </div>
  )
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `npm run build`
Expected: no type errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/GameTable.tsx
git commit -m "feat(frontend): refactor GameTable to circular N-player layout"
```

---

## Task 17: App.tsx + main.tsx + Router + CSS

**Files:**
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/App.tsx`
- Add CSS for new components

- [ ] **Step 1: Update main.tsx**

Read `frontend/src/main.tsx`. Wrap the `<App />` render with `<BrowserRouter>`:

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.tsx'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
```

- [ ] **Step 2: Update App.tsx**

Replace the entire file:

```tsx
import { Route, Routes, useLocation, useParams } from 'react-router-dom'
import { Home } from './components/Home'
import { LobbyRoom } from './components/LobbyRoom'
import { GameTable } from './components/GameTable'

function GameRoute() {
  const { gameId } = useParams<{ gameId: string }>()
  const location = useLocation()
  const statePlayerIndex = (location.state as { playerIndex?: number } | null)?.playerIndex
  const localPlayerIndex =
    statePlayerIndex ??
    parseInt(sessionStorage.getItem('uno_player_index') ?? '0', 10)

  if (!gameId) return <div>Invalid game URL</div>
  return <GameTable gameId={gameId} localPlayerIndex={localPlayerIndex} />
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/lobby/:code" element={<LobbyRoom />} />
      <Route path="/game/:gameId" element={<GameRoute />} />
    </Routes>
  )
}
```

- [ ] **Step 3: Add CSS for new components**

Find the project's CSS file (likely `frontend/src/index.css` or `frontend/src/App.css`). Append:

```css
/* ── Username bar ── */
.username-bar {
  position: fixed;
  top: 12px;
  right: 16px;
  z-index: 100;
  background: rgba(0,0,0,0.45);
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 0.85rem;
  color: #fff;
}
.username-bar__input {
  background: transparent;
  border: none;
  border-bottom: 1px solid rgba(255,255,255,0.6);
  color: #fff;
  font-size: 0.85rem;
  outline: none;
  width: 120px;
}
.username-bar__edit {
  background: none;
  border: none;
  color: rgba(255,255,255,0.6);
  cursor: pointer;
  margin-left: 6px;
  font-size: 0.9rem;
}
.username-bar__placeholder { color: rgba(255,255,255,0.4); }

/* ── Lobby ── */
.lobby {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1a1a2e;
}
.lobby__card {
  background: rgba(255,255,255,0.07);
  border-radius: 16px;
  padding: 40px;
  width: 380px;
  text-align: center;
  color: #fff;
}
.lobby__title { font-size: 1.4rem; margin-bottom: 24px; }
.lobby__code-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 8px;
}
.lobby__code {
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #f5c518;
}
.btn--copy {
  padding: 6px 14px;
  border-radius: 8px;
  background: rgba(255,255,255,0.15);
  border: none;
  color: #fff;
  cursor: pointer;
}
.lobby__hint { font-size: 0.8rem; opacity: 0.55; margin-bottom: 24px; }
.lobby__players { list-style: none; padding: 0; margin: 0 0 24px; }
.lobby__player {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(255,255,255,0.06);
  border-radius: 8px;
  margin-bottom: 6px;
  font-size: 0.95rem;
}
.lobby__host-crown { font-size: 1rem; }
.lobby__waiting { opacity: 0.6; font-size: 0.85rem; }
.lobby__error { color: #ff6b6b; margin-top: 12px; }
.lobby-loading { color: #fff; text-align: center; padding-top: 40vh; }

/* ── Home join input ── */
.setup__join {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
.setup__join-input {
  flex: 1;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid rgba(255,255,255,0.2);
  background: rgba(255,255,255,0.08);
  color: #fff;
  font-size: 0.9rem;
  outline: none;
  letter-spacing: 0.08em;
}
.btn--join {
  padding: 10px 18px;
  border-radius: 10px;
  border: none;
  background: #4a90e2;
  color: #fff;
  cursor: pointer;
  font-size: 0.9rem;
}

/* ── Opponent slot ── */
.opponent-slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  pointer-events: none;
}
.opponent-slot--active .opponent-slot__name {
  color: #f5c518;
  font-weight: 700;
}
.opponent-slot__name {
  font-size: 0.75rem;
  color: rgba(255,255,255,0.8);
  white-space: nowrap;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.opponent-slot__fan {
  position: relative;
}
.card-back {
  border-radius: 6px;
  display: block;
}
.opponent-slot__badge {
  font-size: 0.7rem;
  background: rgba(0,0,0,0.5);
  color: #fff;
  border-radius: 10px;
  padding: 2px 7px;
}
.opponent-slot--active {
  filter: drop-shadow(0 0 8px rgba(245,197,24,0.7));
}

/* ── Local player zone ── */
.player-zone--local {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 12px;
  width: 80%;
}
```

- [ ] **Step 4: Verify TypeScript compiles and dev server starts**

Run: `npm run build`
Expected: no errors.

Run: `npm run dev` — open `http://localhost:5173` in a browser, verify the Home screen loads.

- [ ] **Step 5: Start the backend and do a full smoke test**

Run backend: `./mvnw spring-boot:run` from `backend/`.
Run frontend: `npm run dev` from `frontend/`.

Test flow:
1. Open two browser tabs at `http://localhost:5173`.
2. Tab A: Set a name, click "Create Game" → lobby screen with code.
3. Tab B: Set a name, paste code, click "Join Game" → same lobby screen.
4. Tab A (host): Click "Start Game (2 players)".
5. Both tabs navigate to the game. Each player sees only their own cards at the bottom.
6. Play through a few turns; confirm each player can only play on their turn.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/main.tsx frontend/src/App.tsx frontend/src/index.css
git commit -m "feat(frontend): wire React Router, add circular table layout CSS"
```

---

## Self-Review Checklist

- [x] `GameState.initializeGame(List<String>)` with 2–10 player validation — Task 1
- [x] Lobby create/join/start via REST — Tasks 5–9
- [x] Short code generation with uniqueness check — Task 6
- [x] `X-Session-Id` header on all mutation endpoints — Task 10
- [x] sessionId → playerIndex validation (403 on mismatch) — Task 10
- [x] STOMP config with SockJS at `/ws` using `CorsProperties` — Task 8
- [x] Public broadcast (hands redacted) to `/topic/games/{gameId}` — Task 8
- [x] Private hand to `/topic/games/{gameId}/private/{sessionId}` — Task 8
- [x] Lobby live updates to `/topic/lobbies/{code}` — Task 8, 9
- [x] Persistent username in localStorage — Task 11
- [x] `useGame` merges public + private streams — Task 12
- [x] Circular layout: local player at bottom, opponents on ellipse — Task 16
- [x] Opponents show card backs + count badge — Task 15
- [x] Home screen: create + join with code — Task 13
- [x] Lobby waiting room with live player list and start button — Task 14
- [x] Page refresh recovery: REST on mount + WS reconnect — Task 12, 17
- [x] React Router at `/`, `/lobby/:code`, `/game/:gameId` — Task 17
- [x] `CorsProperties` reused for SockJS allowed origins — Task 8
