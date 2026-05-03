package com.kkhuu131.uno.model;

import java.util.List;
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
