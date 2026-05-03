package com.kkhuu131.uno.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GameStateDrawAndPassTest {

	@Test
	void drawCard_whenDrawStackPending_throws() {
		GameState state = new GameState();
		state.initializeGame();
		Player current = state.getCurrentPlayer();
		state.addPendingDraw(2, DrawType.DRAW_TWO);

		assertThatThrownBy(() -> state.drawCard(current))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("stack");
	}

	@Test
	void passTurn_whenDrawStackPending_resolvesAndAdvancesTurn() {
		GameState state = new GameState();
		state.initializeGame();
		int before = state.getCurrentPlayerIndex();
		Player current = state.getCurrentPlayer();
		state.addPendingDraw(2, DrawType.DRAW_TWO);
		int handSizeBefore = current.getHand().size();

		state.passTurn(current);

		assertThat(current.getHand().size()).isEqualTo(handSizeBefore + 2);
		assertThat(state.getCurrentPlayerIndex()).isNotEqualTo(before);
		assertThat(state.hasPendingDrawStack()).isFalse();
	}
}
