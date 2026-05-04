package com.kkhuu131.uno.backend.exception;

public class LobbyNotFoundException extends RuntimeException {
    public LobbyNotFoundException(String code) {
        super("Lobby not found: " + code);
    }
}
