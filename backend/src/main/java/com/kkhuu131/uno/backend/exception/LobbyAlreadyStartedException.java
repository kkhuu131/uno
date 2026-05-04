package com.kkhuu131.uno.backend.exception;

public class LobbyAlreadyStartedException extends RuntimeException {
    public LobbyAlreadyStartedException() {
        super("Lobby has already started");
    }
}
