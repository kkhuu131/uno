package com.kkhuu131.uno.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkhuu131.uno.backend.exception.GameAlreadyFinishedException;
import com.kkhuu131.uno.backend.support.TestGames;
import com.kkhuu131.uno.backend.web.dto.DrawCardRequest;
import com.kkhuu131.uno.backend.web.dto.PlayCardRequest;
import com.kkhuu131.uno.model.GameState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GameSessionServiceTest {

	@Autowired
	private GameSessionService gameSessionService;

	@Test
	void playCard_whenGameFinished_throwsWithoutMutating() {
		GameState finished = mock(GameState.class);
		when(finished.hasWinner()).thenReturn(true);

		TestGames.register(gameSessionService, "done-play", finished);

		assertThatThrownBy(() -> gameSessionService.playCard("done-play", new PlayCardRequest(0, 0, null)))
				.isInstanceOf(GameAlreadyFinishedException.class)
				.hasMessageContaining("finished");

		verify(finished).hasWinner();
		verify(finished, never()).getPlayers();
	}

	@Test
	void drawCard_whenGameFinished_throwsWithoutMutating() {
		GameState finished = mock(GameState.class);
		when(finished.hasWinner()).thenReturn(true);

		TestGames.register(gameSessionService, "done-draw", finished);

		assertThatThrownBy(() -> gameSessionService.drawCard("done-draw", new DrawCardRequest(0, false)))
				.isInstanceOf(GameAlreadyFinishedException.class);

		verify(finished).hasWinner();
		verify(finished, never()).getPlayers();
	}
}
