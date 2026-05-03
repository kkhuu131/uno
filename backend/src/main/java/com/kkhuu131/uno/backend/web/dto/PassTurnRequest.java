package com.kkhuu131.uno.backend.web.dto;

import jakarta.validation.constraints.Min;

/** Body for {@code POST /api/games/{gameId}/pass}. */
public record PassTurnRequest(@Min(0) int playerIndex) {}
