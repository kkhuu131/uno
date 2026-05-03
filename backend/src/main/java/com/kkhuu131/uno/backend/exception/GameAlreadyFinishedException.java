package com.kkhuu131.uno.backend.exception;

/**
 * Thrown when a mutating action is requested after {@link com.kkhuu131.uno.model.GameState#hasWinner()} is true.
 * Mapped to HTTP 409 Conflict by {@link ApiExceptionHandler}.
 */
public class GameAlreadyFinishedException extends RuntimeException {

	public GameAlreadyFinishedException() {
		super("Game already finished");
	}
}
