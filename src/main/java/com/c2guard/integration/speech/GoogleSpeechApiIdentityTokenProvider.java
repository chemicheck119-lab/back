package com.c2guard.integration.speech;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

final class GoogleSpeechApiIdentityTokenProvider implements SpeechApiIdentityTokenProvider {

    private final URI audience;
    private final IdTokenCredentials credentials;

    GoogleSpeechApiIdentityTokenProvider(String audience) throws IOException {
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("Speech API IAM audience must not be blank");
        }
        this.audience = URI.create(audience);
        GoogleCredentials applicationDefault = GoogleCredentials.getApplicationDefault();
        if (!(applicationDefault instanceof IdTokenProvider idTokenProvider)) {
            throw new IOException("Application Default Credentials cannot issue ID tokens");
        }
        this.credentials = IdTokenCredentials.newBuilder()
                .setIdTokenProvider(idTokenProvider)
                .setTargetAudience(audience)
                .build();
    }

    @Override
    public String authorizationHeader() throws IOException {
        Map<String, List<String>> metadata = credentials.getRequestMetadata(audience);
        List<String> authorization = metadata.get("Authorization");
        if (authorization == null || authorization.isEmpty() || authorization.get(0).isBlank()) {
            throw new IOException("ID token credentials returned no Authorization header");
        }
        return authorization.get(0);
    }
}
