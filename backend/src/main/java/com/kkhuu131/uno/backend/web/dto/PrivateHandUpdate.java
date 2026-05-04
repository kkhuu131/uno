package com.kkhuu131.uno.backend.web.dto;

import java.util.List;

public record PrivateHandUpdate(String gameId, int playerIndex, List<CardView> hand) {}
