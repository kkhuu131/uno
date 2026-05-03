package com.kkhuu131.uno.backend.web;

import com.kkhuu131.uno.backend.service.GameSessionService;
import com.kkhuu131.uno.backend.web.dto.ErrorResponse;
import com.kkhuu131.uno.backend.web.dto.GameCreatedResponse;
import com.kkhuu131.uno.backend.web.dto.DrawCardRequest;
import com.kkhuu131.uno.backend.web.dto.DrawCardResponse;
import com.kkhuu131.uno.backend.web.dto.GameSnapshotResponse;
import com.kkhuu131.uno.backend.web.dto.PlayCardRequest;
import com.kkhuu131.uno.backend.web.dto.CardView;
import com.kkhuu131.uno.model.GameState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h2>Minimal Spring Boot “slice” for learning</h2>
 *
 * <p><b>What happens at startup</b><br>
 * Spring scans {@code com.kkhuu131.uno.backend} (and subpackages), finds this class and
 * {@link com.kkhuu131.uno.backend.service.GameSessionService}, creates one instance of each, and
 * passes the service into this controller’s constructor.</p>
 *
 * <p><b>What happens on each HTTP request</b><br>
 * Tomcat receives the request → Spring picks the method whose path + verb match → your method runs
 * → the return value is turned into JSON (for objects/records) or plain text (for {@link String}).</p>
 *
 * <p><b>Try it</b> (with the app running on port 8080):</p>
 * <pre>
 * curl http://localhost:8080/api/hello
 * curl -X POST http://localhost:8080/api/games
 * curl http://localhost:8080/api/games/PASTE_GAME_ID_HERE
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class GameController {

	private final GameSessionService gameSessionService;
	private final GameSnapshotMapper snapshotMapper;

	public GameController(GameSessionService gameSessionService, GameSnapshotMapper snapshotMapper) {
		this.gameSessionService = gameSessionService;
		this.snapshotMapper = snapshotMapper;
	}

	/** Simplest endpoint: proves the server and mapping work. */
	@GetMapping("/hello")
	public String hello() {
		return "Hello from UNO backend";
	}

	/**
	 * Creates a new in-memory game using your {@link GameState} rules.
	 * HTTP 201 Created + JSON body {@code {"gameId":"..."}} .
	 */
	@PostMapping("/games")
	public ResponseEntity<GameCreatedResponse> createGame() {
		String id = gameSessionService.createGame();
		return ResponseEntity.status(HttpStatus.CREATED).body(new GameCreatedResponse(id));
	}

	/**
	 * Loads a game by id. Returns 404 if the id is unknown (wrong id or server restarted).
	 */
	@GetMapping("/games/{gameId}")
	public ResponseEntity<GameSnapshotResponse> getGame(@PathVariable String gameId) {
		return gameSessionService
				.findGame(gameId)
				.map(state -> ResponseEntity.ok(snapshotMapper.toSnapshot(gameId, state)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	/**
	 * Draw one card from the deck into the given player's hand. Does not end the turn (same as {@link
	 * com.kkhuu131.uno.model.GameState#drawCard}; your UI can chain draw then play in separate requests).
	 */
	@PostMapping("/games/{gameId}/draw")
	public ResponseEntity<?> drawCard(@PathVariable String gameId, @RequestBody DrawCardRequest body) {
		try {
			return gameSessionService
					.drawCard(gameId, body)
					.<ResponseEntity<?>>map(
							outcome ->
									ResponseEntity.ok(
											new DrawCardResponse(
													CardView.from(outcome.drawnCard()),
													snapshotMapper.toSnapshot(gameId, outcome.state()))))
					.orElse(ResponseEntity.notFound().build());
		} catch (IllegalArgumentException | IllegalStateException e) {
			return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
		}
	}

	/**
	 * Play a card from a player's hand. On success, returns the same fields as {@link #getGame(String)}.
	 * On rule violations, HTTP 400 with {@link ErrorResponse}.
	 */
	@PostMapping("/games/{gameId}/play")
	public ResponseEntity<?> playCard(@PathVariable String gameId, @RequestBody PlayCardRequest body) {
		try {
			return gameSessionService
					.playCard(gameId, body)
					.<ResponseEntity<?>>map(state -> ResponseEntity.ok(snapshotMapper.toSnapshot(gameId, state)))
					.orElse(ResponseEntity.notFound().build());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
		}
	}
}
