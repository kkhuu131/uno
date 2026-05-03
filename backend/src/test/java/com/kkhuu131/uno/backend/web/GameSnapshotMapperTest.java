package com.kkhuu131.uno.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kkhuu131.uno.model.Color;
import com.kkhuu131.uno.model.GameState;
import com.kkhuu131.uno.model.NumberCard;
import com.kkhuu131.uno.model.Player;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fast, framework-free tests for snapshot mapping (especially FINISHED) without simulating full games. */
class GameSnapshotMapperTest {

	private final GameSnapshotMapper mapper = new GameSnapshotMapper();

	@Test
	void toSnapshot_whenFinished_includesWinnerIndexAndName() {
		Player winner = mock(Player.class);
		when(winner.getName()).thenReturn("Winner");
		when(winner.getHand()).thenReturn(List.of());

		Player other = mock(Player.class);
		when(other.getName()).thenReturn("Other");
		when(other.getHand()).thenReturn(List.of());

		GameState state = mock(GameState.class);
		when(state.hasWinner()).thenReturn(true);
		when(state.getWinner()).thenReturn(winner);
		when(state.getPlayers()).thenReturn(List.of(winner, other));
		when(state.getCurrentPlayerIndex()).thenReturn(1);
		when(state.getActiveColor()).thenReturn(Color.RED);
		NumberCard top = new NumberCard(Color.BLUE, 8);
		when(state.getDiscardPile()).thenReturn(new ArrayList<>(List.of(top)));

		var snap = mapper.toSnapshot("game-1", state);

		assertThat(snap.gameId()).isEqualTo("game-1");
		assertThat(snap.status()).isEqualTo("FINISHED");
		assertThat(snap.winnerPlayerIndex()).isZero();
		assertThat(snap.winnerName()).isEqualTo("Winner");
		assertThat(snap.topDiscard().kind()).isEqualTo("NUMBER");
		assertThat(snap.players()).hasSize(2);
	}
}
