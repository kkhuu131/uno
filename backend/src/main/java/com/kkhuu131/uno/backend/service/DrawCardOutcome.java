package com.kkhuu131.uno.backend.service;

import com.kkhuu131.uno.model.Card;
import com.kkhuu131.uno.model.GameState;

/**
 * Pair returned by {@link GameSessionService#drawCard(String, com.kkhuu131.uno.backend.web.dto.DrawCardRequest)} so the
 * web layer can build {@link com.kkhuu131.uno.backend.web.dto.DrawCardResponse} without re-querying.
 *
 * <p>{@code mustDrawAgain} is true when the player is in forced-draw mode and the drawn card is not playable —
 * the client should keep drawing one card at a time until it is false.
 *
 * <p>{@code deckReshuffled} is true when the discard pile was recycled back into the draw pile for this draw.
 */
public record DrawCardOutcome(Card drawnCard, GameState state, boolean mustDrawAgain, boolean deckReshuffled) {}
