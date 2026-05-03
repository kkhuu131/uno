package com.kkhuu131.uno.backend.exception;

public class LobbyAlreadyStartedException extends RuntimeException {
    public LobbyAlreadyStartedException() {
        super("Game has already started");
    }
}
