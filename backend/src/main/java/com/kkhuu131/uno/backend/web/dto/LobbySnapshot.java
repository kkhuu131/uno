package com.kkhuu131.uno.backend.web.dto;

import java.util.List;

public record LobbySnapshot(
    String code,
    int hostPlayerIndex,
    List<LobbyPlayerView> players,
    String status,
    String gameId
) {}
