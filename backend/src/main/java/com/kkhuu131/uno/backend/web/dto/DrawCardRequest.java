package com.kkhuu131.uno.backend.web.dto;

import jakarta.validation.constraints.Min;

/**
 * Body for {@code POST /api/games/{gameId}/draw}.
 *
 * <p>{@code playerIndex} must be the player taking the turn (match {@link GameSnapshotResponse#currentPlayerIndex}),
 * or {@link com.kkhuu131.uno.model.GameState#drawCard} will reject the action.
 *
 * <p>If {@code endTurn} is true, the turn advances after drawing (typical “draw one and pass” when the drawn card is
 * not played). Not allowed while a +2/+4 stack is pending — use {@code POST .../pass} instead.
 *
 * <p>{@code endTurn} is {@link Boolean} so JSON may omit it (treated as false).
 */
public record DrawCardRequest(@Min(0) int playerIndex, Boolean endTurn) {
	public DrawCardRequest {
		endTurn = Boolean.TRUE.equals(endTurn) ? Boolean.TRUE : Boolean.FALSE;
	}
}
