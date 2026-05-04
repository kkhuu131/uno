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
}
