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
import org.springframework.web.bind.annotation.ResponseStatus;
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
}
