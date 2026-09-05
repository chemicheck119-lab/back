package com.c2guard.integration.model;

import java.io.IOException;

@FunctionalInterface
interface ModelApiIdentityTokenProvider {

    String authorizationHeader() throws IOException;

    static ModelApiIdentityTokenProvider disabled() {
        return () -> "";
    }
}
