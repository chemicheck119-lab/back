package com.c2guard.bff.record;

import com.c2guard.security.BffUserPrincipal;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
class RecordFingerprint {

    String calculate(String incidentId, RecordSaveRequest request,
                     BffUserPrincipal principal) {
        MessageDigest digest = sha256();
        add(digest, incidentId);
        add(digest, principal.userId());
        add(digest, principal.organizationId());
        add(digest, request.conversationStartedAt().toInstant().toString());
        for (RecordSaveRequest.ConversationMessage message : request.messages()) {
            add(digest, message.messageId());
            add(digest, message.sequence().toString());
            add(digest, message.role().name());
            add(digest, message.text());
            add(digest, message.createdAt().toInstant().toString());
            add(digest, message.analysisId());
        }
        for (String analysisId : request.analysisIds()) {
            add(digest, analysisId);
        }
        for (String confirmationId : request.confirmationIds()) {
            add(digest, confirmationId);
        }
        StructuredIncidentOutcome outcome = request.outcomeReport();
        add(digest, outcome.facilityName());
        add(digest, outcome.facilityAddress());
        outcome.performedActions().stream().map(Enum::name).sorted()
                .forEach(value -> add(digest, value));
        add(digest, outcome.briefApplicationStatus().name());
        outcome.additionalFactors().stream().map(Enum::name).sorted()
                .forEach(value -> add(digest, value));
        add(digest, outcome.finalResponseOutcome().name());
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private void add(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", error);
        }
    }
}
