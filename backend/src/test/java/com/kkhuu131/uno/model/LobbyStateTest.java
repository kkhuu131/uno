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
