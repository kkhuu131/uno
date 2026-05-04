package com.kkhuu131.uno.backend.web.dto;

/**
 * Result of a successful draw: the card that was taken from the pile plus the updated table state.
 *
 * <p>{@code mustDrawAgain} is true when the player has no playable card yet and must call draw again.
 * The client should loop — drawing one card at a time with animation — until this is false.
 *
 * <p>{@code deckReshuffled} is true when the discard pile was recycled back into the draw pile for this draw.
 * The client can use this to show a shuffle animation before the next card appears.
 */
public record DrawCardResponse(CardView drawnCard, GameSnapshotResponse game, boolean mustDrawAgain, boolean deckReshuffled) {}
