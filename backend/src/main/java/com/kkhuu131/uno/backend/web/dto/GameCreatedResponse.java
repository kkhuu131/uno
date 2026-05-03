package com.kkhuu131.uno.backend.web.dto;

/**
 * JSON returned after {@code POST /api/games}. Only contains what the client needs on the wire.
 * Jackson turns this record into JSON automatically.
 */
public record GameCreatedResponse(String gameId) {}
