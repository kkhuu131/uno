package com.kkhuu131.uno.backend.web.dto;

import java.util.List;

/** One seat at the table: display name and cards in hand (index = {@code handIndex} when playing). */
public record PlayerStateView(String name, List<CardView> hand) {}
