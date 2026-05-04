package com.kkhuu131.uno.backend.service;

import com.kkhuu131.uno.backend.exception.GameAlreadyFinishedException;
import com.kkhuu131.uno.backend.web.dto.DrawCardRequest;
import com.kkhuu131.uno.backend.web.dto.PassTurnRequest;
import com.kkhuu131.uno.backend.web.dto.PlayCardRequest;
import com.kkhuu131.uno.model.Card;
import com.kkhuu131.uno.model.Color;
import com.kkhuu131.uno.model.GameState;
import com.kkhuu131.uno.model.Player;
import com.kkhuu131.uno.model.LobbyPlayer;
import com.kkhuu131.uno.model.WildCard;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Holds active games in memory. Spring creates one instance and injects it into your controller.
 * <p>
 * {@link Service} = “this class does application logic” (not HTTP). Same idea as putting logic in a
 * plain Java class, plus Spring knows to register it as a singleton bean.
 */
@Service
public class GameSessionService {

	private final Map<String, GameState> games = new ConcurrentHashMap<>();
	private final Map<String, Map<String, Integer>> sessionMaps = new ConcurrentHashMap<>();

	/**
	 * Creates a new game, runs your domain rules ({@link GameState#initializeGame()}), and stores it.
	 *
	 * @return the id clients use in URLs such as {@code GET /api/games/{gameId}}
	 */
	public String createGame() {
		String id = UUID.randomUUID().toString();
		GameState state = new GameState();
		state.initializeGame();
		games.put(id, state);
		return id;
	}

	/**
	 * Creates a game from a lobby's player list and records the sessionId↔playerIndex mapping
	 * so mutation endpoints can validate callers.
	 */
	public String createGame(List<LobbyPlayer> lobbyPlayers) {
		String id = UUID.randomUUID().toString();
		GameState state = new GameState();
		state.initializeGame(lobbyPlayers.stream().map(LobbyPlayer::displayName).toList());
		games.put(id, state);

		Map<String, Integer> sessionMap = new ConcurrentHashMap<>();
		for (LobbyPlayer p : lobbyPlayers) {
			sessionMap.put(p.sessionId(), p.playerIndex());
		}
		sessionMaps.put(id, sessionMap);
		return id;
	}

	/** Returns the playerIndex for the given sessionId in this game, or empty if not found. */
	public Optional<Integer> getPlayerIndex(String gameId, String sessionId) {
		Map<String, Integer> map = sessionMaps.get(gameId);
		if (map == null) return Optional.empty();
		return Optional.ofNullable(map.get(sessionId));
	}

	/** Returns the full sessionId→playerIndex map (empty map if game has no session mappings). */
	public Map<String, Integer> getSessionMap(String gameId) {
		return sessionMaps.getOrDefault(gameId, Map.of());
	}

	public Optional<GameState> findGame(String gameId) {
		return Optional.ofNullable(games.get(gameId));
	}

	/**
	 * Plays one card from {@code playerIndex}'s hand at {@code handIndex}. Mutates the stored {@link GameState}.
	 *
	 * @return empty if {@code gameId} is not found
	 * @throws GameAlreadyFinishedException if {@link GameState#hasWinner()} is already true
	 * @throws IllegalArgumentException rule violations, wrong player, bad indices, missing color for wild, etc.
	 */
	public Optional<GameState> playCard(String gameId, PlayCardRequest request) {
		GameState state = games.get(gameId);
		if (state == null) {
			return Optional.empty();
		}
		requireInProgress(state);
		List<Player> players = state.getPlayers();
		if (request.playerIndex() < 0 || request.playerIndex() >= players.size()) {
			throw new IllegalArgumentException("playerIndex out of range");
		}
		Player player = players.get(request.playerIndex());
		List<Card> hand = player.getHand();
		if (request.handIndex() < 0 || request.handIndex() >= hand.size()) {
			throw new IllegalArgumentException("handIndex out of range");
		}
		Card card = hand.get(request.handIndex());
		if (card instanceof WildCard wc) {
			Color chosen = parseChosenColor(request.chosenColor());
			state.playCard(player, wc, chosen);
		} else {
			if (request.chosenColor() != null && !request.chosenColor().isBlank()) {
				throw new IllegalArgumentException("chosenColor is only used when playing a wild card");
			}
			state.playCard(player, card);
		}
		return Optional.of(state);
	}

	/**
	 * Draws one card from the deck for {@code playerIndex}. Only the current player may draw (enforced by
	 * {@link GameState#drawCard(Player)}).
	 *
	 * @return empty if {@code gameId} is not found
	 * @throws GameAlreadyFinishedException if {@link GameState#hasWinner()} is already true
	 * @throws IllegalArgumentException wrong player, bad index, empty deck with nothing to recycle, etc.
	 */
	public Optional<DrawCardOutcome> drawCard(String gameId, DrawCardRequest request) {
		GameState state = games.get(gameId);
		if (state == null) {
			return Optional.empty();
		}
		requireInProgress(state);
		List<Player> players = state.getPlayers();
		if (request.playerIndex() < 0 || request.playerIndex() >= players.size()) {
			throw new IllegalArgumentException("playerIndex out of range");
		}
		Player player = players.get(request.playerIndex());
		Card drawn = state.drawCard(player);
		boolean mustDrawAgain = state.isForcedDrawActive() && !state.isPlayable(drawn);
		boolean deckReshuffled = state.wasLastDrawReshuffle();
		if (Boolean.TRUE.equals(request.endTurn())) {
			if (mustDrawAgain) {
				throw new IllegalStateException(
						"You must keep drawing until you find a playable card");
			}
			if (state.hasPendingDrawStack()) {
				throw new IllegalStateException(
						"Cannot end turn after draw while a draw stack is pending; use POST /api/games/{id}/pass");
			}
			state.endTurn();
		}
		return Optional.of(new DrawCardOutcome(drawn, state, mustDrawAgain, deckReshuffled));
	}

	/**
	 * Pass the turn (after an optional draw, or to take a stacked +2/+4). Mutates {@link GameState}.
	 *
	 * @return empty if {@code gameId} is not found
	 * @throws GameAlreadyFinishedException if {@link GameState#hasWinner()} is already true
	 */
	public Optional<GameState> passTurn(String gameId, PassTurnRequest request) {
		GameState state = games.get(gameId);
		if (state == null) {
			return Optional.empty();
		}
		requireInProgress(state);
		List<Player> players = state.getPlayers();
		if (request.playerIndex() < 0 || request.playerIndex() >= players.size()) {
			throw new IllegalArgumentException("playerIndex out of range");
		}
		Player player = players.get(request.playerIndex());
		state.passTurn(player);
		return Optional.of(state);
	}

	private static void requireInProgress(GameState state) {
		if (state.hasWinner()) {
			throw new GameAlreadyFinishedException();
		}
	}

	private static Color parseChosenColor(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("Wild cards require chosenColor (RED, GREEN, BLUE, or YELLOW)");
		}
		try {
			return Color.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid chosenColor: use RED, GREEN, BLUE, or YELLOW");
		}
	}
}
