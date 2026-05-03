package com.kkhuu131.uno.backend.web.dto;

/**
 * Body for {@code POST /api/games/{gameId}/play}.
 *
 * <p>Use the index of the player (same order as {@link GameSnapshotResponse#players()}) and the index of the
 * card in that player's {@link PlayerStateView#hand()} list (0-based). For wild / wild +4, set {@code chosenColor}
 * to {@code RED}, {@code GREEN}, {@code BLUE}, or {@code YELLOW}.
 */
public record PlayCardRequest(int playerIndex, int handIndex, String chosenColor) {}
