package com.kkhuu131.uno.backend.web.dto;

/**
 * Result of a successful draw: the card that was taken from the pile plus the updated table state (same shape as
 * {@link GameSnapshotResponse} from {@code GET /api/games/{id}}).
 */
public record DrawCardResponse(CardView drawnCard, GameSnapshotResponse game) {}
