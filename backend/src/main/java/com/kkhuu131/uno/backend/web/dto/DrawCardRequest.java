package com.kkhuu131.uno.backend.web.dto;

/**
 * Body for {@code POST /api/games/{gameId}/draw}.
 *
 * <p>{@code playerIndex} must be the player taking the turn (match {@link GameSnapshotResponse#currentPlayerIndex}),
 * or {@link com.kkhuu131.uno.model.GameState#drawCard} will reject the action.
 */
public record DrawCardRequest(int playerIndex) {}
