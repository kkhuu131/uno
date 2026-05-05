package com.kkhuu131.uno.backend.service;

import com.kkhuu131.uno.backend.exception.ForbiddenActionException;
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
        LobbyState lobby = lobbySessionService.createLobby("s1-join", "Host");
        String code = lobby.getCode();

        LobbyState after = lobbySessionService.joinLobby(code, "s2-join", "Guest");

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
        LobbyState lobby = lobbySessionService.createLobby("s1-started", "Host");
        lobbySessionService.joinLobby(lobby.getCode(), "s2-started", "Guest");
        lobbySessionService.startGame(lobby.getCode(), "s1-started", gameSessionService);

        assertThatThrownBy(() -> lobbySessionService.joinLobby(lobby.getCode(), "s3", "Late"))
            .isInstanceOf(LobbyAlreadyStartedException.class);
    }

    @Test
    void startGame_byNonHost_throws() {
        LobbyState lobby = lobbySessionService.createLobby("host-session-fg", "Host");
        lobbySessionService.joinLobby(lobby.getCode(), "other-session-fg", "Other");

        assertThatThrownBy(() ->
            lobbySessionService.startGame(lobby.getCode(), "other-session-fg", gameSessionService))
            .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void startGame_withOnePlayer_throws() {
        LobbyState lobby = lobbySessionService.createLobby("only-host-sg", "Solo");

        assertThatThrownBy(() ->
            lobbySessionService.startGame(lobby.getCode(), "only-host-sg", gameSessionService))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("2");
    }

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

        // Force game into finished state by emptying a player's hand via reflection
        com.kkhuu131.uno.model.GameState game =
                gameSessionService.findGame(gameId).orElseThrow();
        java.util.List<com.kkhuu131.uno.model.Player> players =
                (java.util.List<com.kkhuu131.uno.model.Player>) org.springframework.test.util.ReflectionTestUtils
                        .getField(game, "players");
        java.util.List<com.kkhuu131.uno.model.Card> hand =
                (java.util.List<com.kkhuu131.uno.model.Card>) org.springframework.test.util.ReflectionTestUtils
                        .getField(players.get(0), "hand");
        hand.clear();

        LobbyState result = lobbySessionService.resetLobby(lobby.getCode(), gameSessionService);

        assertThat(result.getStatus()).isEqualTo(LobbyStatus.WAITING);
        assertThat(result.getGameId()).isNull();
    }
}
