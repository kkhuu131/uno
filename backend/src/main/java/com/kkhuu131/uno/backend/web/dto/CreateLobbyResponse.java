package com.kkhuu131.uno.backend.web.dto;

public record CreateLobbyResponse(String code, int playerIndex, LobbySnapshot lobby) {}
