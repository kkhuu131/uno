package com.kkhuu131.uno.backend.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kkhuu131.uno.backend.config.CorsProperties;
import com.kkhuu131.uno.backend.config.WebConfig;
import com.kkhuu131.uno.backend.exception.ApiExceptionHandler;
import com.kkhuu131.uno.backend.exception.GameAlreadyFinishedException;
import com.kkhuu131.uno.backend.service.GameSessionService;
import com.kkhuu131.uno.backend.web.dto.DrawCardRequest;
import com.kkhuu131.uno.backend.web.dto.PlayCardRequest;
import com.kkhuu131.uno.model.GameState;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MVC slice: only {@link GameController} + web stack. {@link GameSessionService} is mocked so we control the
 * {@link GameState} returned from {@code findGame}.
 */
@WebMvcTest(controllers = GameController.class)
@Import({WebConfig.class, GameSnapshotMapper.class, ApiExceptionHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
class GameControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GameSessionService gameSessionService;

	@Test
	void getGame_returnsSnapshotWithPlayersTopDiscardAndStatus() throws Exception {
		GameState state = new GameState();
		state.initializeGame();
		when(gameSessionService.findGame("game-test")).thenReturn(Optional.of(state));

		mockMvc.perform(get("/api/games/game-test"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gameId").value("game-test"))
				.andExpect(jsonPath("$.currentPlayerIndex").exists())
				.andExpect(jsonPath("$.activeColor").exists())
				.andExpect(jsonPath("$.players").isArray())
				.andExpect(jsonPath("$.players.length()").value(2))
				.andExpect(jsonPath("$.players[0].name").exists())
				.andExpect(jsonPath("$.players[0].hand").isArray())
				.andExpect(jsonPath("$.players[0].hand.length()").value(7))
				.andExpect(jsonPath("$.topDiscard").exists())
				.andExpect(jsonPath("$.topDiscard.kind").exists())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.winnerPlayerIndex").value(nullValue()))
				.andExpect(jsonPath("$.winnerName").value(nullValue()))
				.andExpect(jsonPath("$.pendingDrawStack").value(false));
	}

	@Test
	void getGame_unknownId_returns404() throws Exception {
		when(gameSessionService.findGame("missing")).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/games/missing")).andExpect(status().isNotFound());
	}

	@Test
	void play_whenGameAlreadyFinished_returns409() throws Exception {
		when(gameSessionService.playCard(eq("g1"), any(PlayCardRequest.class)))
				.thenThrow(new GameAlreadyFinishedException());

		mockMvc.perform(
						post("/api/games/g1/play")
								.contentType(MediaType.APPLICATION_JSON)
								.content(
										"""
										{"playerIndex":0,"handIndex":0,"chosenColor":null}
										"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Game already finished"));
	}

	@Test
	void draw_whenGameAlreadyFinished_returns409() throws Exception {
		when(gameSessionService.drawCard(eq("g2"), any(DrawCardRequest.class)))
				.thenThrow(new GameAlreadyFinishedException());

		mockMvc.perform(
						post("/api/games/g2/draw")
								.contentType(MediaType.APPLICATION_JSON)
								.content(
										"""
										{"playerIndex":0}
										"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Game already finished"));
	}

	@Test
	void play_invalidBody_negativeIndex_returns400() throws Exception {
		mockMvc.perform(
						post("/api/games/g1/play")
								.contentType(MediaType.APPLICATION_JSON)
								.content(
										"""
										{"playerIndex":-1,"handIndex":0,"chosenColor":null}
										"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").exists());
	}
}
