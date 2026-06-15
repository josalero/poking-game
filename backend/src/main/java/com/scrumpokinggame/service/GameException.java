package com.scrumpokinggame.service;

import org.springframework.http.HttpStatus;

public final class GameException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public GameException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}

