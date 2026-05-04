package com.kkhuu131.uno.backend.exception;

public class ForbiddenActionException extends RuntimeException {
    public ForbiddenActionException() {
        super("Action not allowed for this player");
    }
}
