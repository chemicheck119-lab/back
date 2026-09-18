package com.c2guard.bff.phone;

import com.c2guard.bff.common.BffContractException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class JdbcPhoneTranscriptStore implements PhoneTranscriptStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPhoneTranscriptStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StoredTranscript> findByProviderEvent(String provider, String eventId) {
        return jdbcTemplate.query("""
                        SELECT transcript_id, incident_id, provider, provider_call_id,
                               provider_event_id, transcript_text, language, is_final,
                               review_status, accepted_at, received_request_id
                        FROM incident_phone_transcripts
                        WHERE provider = ? AND provider_event_id = ?
                        """, (resultSet, rowNumber) -> new StoredTranscript(
                        resultSet.getString("transcript_id"),
                        resultSet.getString("incident_id"),
                        resultSet.getString("provider"),
                        resultSet.getString("provider_call_id"),
                        resultSet.getString("provider_event_id"),
                        resultSet.getString("transcript_text"),
                        resultSet.getString("language"),
                        resultSet.getBoolean("is_final"),
                        resultSet.getString("review_status"),
                        resultSet.getObject("accepted_at", OffsetDateTime.class),
                        resultSet.getString("received_request_id")), provider, eventId)
                .stream().findFirst();
    }

    @Override
    public StoredTranscript save(String incidentId, PhoneTranscriptIngressRequest request,
                                 String transcriptId, String requestId,
                                 OffsetDateTime acceptedAt) {
        Integer incidentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incidents WHERE incident_id = ?", Integer.class, incidentId);
        if (incidentCount == null || incidentCount != 1) {
            throw new BffContractException(404, "INCIDENT_NOT_FOUND",
                    "연결할 사고를 찾을 수 없습니다.", false);
        }
        String reviewStatus = request.isFinal() ? "PENDING_REVIEW" : "INTERIM";
        try {
            jdbcTemplate.update("""
                            INSERT INTO incident_phone_transcripts (
                                transcript_id, incident_id, provider, provider_call_id,
                                provider_event_id, transcript_text, language, is_final,
                                segment_index, occurred_at, accepted_at, received_request_id,
                                review_status
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, transcriptId, incidentId, request.provider(), request.callId(),
                    request.eventId(), request.text(), request.language(), request.isFinal(),
                    request.segmentIndex(), request.occurredAt(), acceptedAt, requestId,
                    reviewStatus);
        } catch (DuplicateKeyException duplicate) {
            return findByProviderEvent(request.provider(), request.eventId())
                    .orElseThrow(() -> duplicate);
        }
        return new StoredTranscript(transcriptId, incidentId, request.provider(), request.callId(),
                request.eventId(), request.text(), request.language(), request.isFinal(),
                reviewStatus, acceptedAt, requestId);
    }
}
