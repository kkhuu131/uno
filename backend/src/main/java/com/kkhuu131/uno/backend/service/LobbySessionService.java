package com.kkhuu131.uno.backend.service;

import com.kkhuu131.uno.backend.exception.ForbiddenActionException;
import com.kkhuu131.uno.backend.exception.LobbyAlreadyStartedException;
import com.kkhuu131.uno.backend.exception.LobbyFullException;
import com.kkhuu131.uno.backend.exception.LobbyNotFoundException;
import com.kkhuu131.uno.model.GameState;
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

    /**
     * Starts the game for the lobby. Delegates to
     * {@link GameSessionService#createGame(java.util.List)} (added in Task 7).
     * Until that overload exists, falls back to the no-arg overload so the lobby
     * can transition to IN_PROGRESS and tests that depend on this state still pass.
     */
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

    public void leaveLobby(String code, String sessionId) {
        LobbyState lobby = lobbies.get(code);
        if (lobby == null) return;
        lobby.removePlayer(sessionId);
        if (lobby.getPlayerCount() == 0) {
            lobbies.remove(code);
        }
    }

    public Optional<LobbyState> resetLobby(String code, GameSessionService gameSessionService) {
        LobbyState lobby = requireLobby(code);
        if (lobby.getStatus() != LobbyStatus.IN_PROGRESS) return Optional.empty();
        boolean gameFinished = gameSessionService.findGame(lobby.getGameId())
                .map(GameState::hasWinner)
                .orElse(false);
        if (!gameFinished) return Optional.empty();
        lobby.resetForRematch();
        return Optional.of(lobby);
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
