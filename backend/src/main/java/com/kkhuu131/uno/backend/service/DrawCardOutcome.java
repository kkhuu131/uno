package com.kkhuu131.uno.backend.service;

import com.kkhuu131.uno.model.Card;
import com.kkhuu131.uno.model.GameState;

/**
 * Pair returned by {@link GameSessionService#drawCard(String, com.kkhuu131.uno.backend.web.dto.DrawCardRequest)} so the
 * web layer can build {@link com.kkhuu131.uno.backend.web.dto.DrawCardResponse} without re-querying.
 */
public record DrawCardOutcome(Card drawnCard, GameState state) {}
