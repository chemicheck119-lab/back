package com.c2guard.integration.speech;

import java.io.IOException;

@FunctionalInterface
interface SpeechApiIdentityTokenProvider {

    String authorizationHeader() throws IOException;

    static SpeechApiIdentityTokenProvider disabled() {
        return () -> "";
    }
}
