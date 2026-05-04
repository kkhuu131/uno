package com.kkhuu131.uno.backend.exception;

public class LobbyFullException extends RuntimeException {
    public LobbyFullException() {
        super("Lobby is full");
    }
}
