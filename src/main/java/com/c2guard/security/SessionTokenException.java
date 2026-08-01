package com.c2guard.security;

final class SessionTokenException extends RuntimeException {

    SessionTokenException() {
        super("invalid service session");
    }
}
