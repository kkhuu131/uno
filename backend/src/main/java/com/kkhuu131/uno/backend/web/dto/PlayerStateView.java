package com.kkhuu131.uno.backend.web.dto;

import java.util.List;

/**
 * One seat at the table. {@code hand} is {@code null} when the player's cards are redacted
 * (opponents in a real-multiplayer game). {@code handSize} is always accurate.
 */
public record PlayerStateView(String name, List<CardView> hand, int handSize) {}
