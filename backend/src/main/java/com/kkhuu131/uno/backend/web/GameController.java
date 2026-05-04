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
        if (sessionMap.isEmpty()) return;
        if (sessionId == null || !Integer.valueOf(claimedPlayerIndex).equals(sessionMap.get(sessionId))) {
            throw new ForbiddenActionException();
        }
    }
}
