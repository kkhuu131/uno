package com.kkhuu131.uno.backend.web.dto;

import java.util.List;

/**
 * Read-only view for {@code GET /api/games/{gameId}} and successful {@code POST .../play}.
 *
 * <p>{@link #players()} lists seats in order (same indices as {@code playerIndex} in {@link PlayCardRequest}).
 * Each {@link PlayerStateView#hand()} is ordered; index {@code i} is the {@code handIndex} to send when playing
 * that card.
 *
 * <p>{@link #status()} is {@code IN_PROGRESS} until someone has an empty hand, then {@code FINISHED}. When finished,
 * {@link #winnerPlayerIndex()} and {@link #winnerName()} identify the winner (same order as {@link #players()}).
 */
public record GameSnapshotResponse(
		String gameId,
		int currentPlayerIndex,
		String activeColor,
		List<PlayerStateView> players,
		CardView topDiscard,
		String status,
		Integer winnerPlayerIndex,
		String winnerName
) {}
