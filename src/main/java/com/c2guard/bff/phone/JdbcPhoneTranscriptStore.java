package com.c2guard.bff.phone;

import com.c2guard.bff.common.BffContractException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPhoneTranscriptStore implements PhoneTranscriptStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPhoneTranscriptStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StoredTranscript> findByProviderEvent(String provider, String eventId) {
        return query("""
                        SELECT * FROM incident_phone_transcripts
                        WHERE provider = ? AND provider_event_id = ?
                        """, provider, eventId)
                .stream().findFirst();
    }

    @Override
    public Optional<StoredTranscript> findById(String incidentId, String transcriptId) {
        return query("""
                        SELECT * FROM incident_phone_transcripts
                        WHERE incident_id = ? AND transcript_id = ?
                        """, incidentId, transcriptId).stream().findFirst();
    }

    @Override
    public List<StoredTranscript> findReplayEvents(String incidentId, String lastEventId,
                                                   int limit) {
        String cursorTranscriptId = transcriptId(lastEventId);
        long cursorRevision = revision(lastEventId);
        if (cursorTranscriptId == null) {
            return jdbcTemplate.query("""
                    SELECT * FROM (
                        SELECT * FROM incident_phone_transcripts
                        WHERE incident_id = ?
                        ORDER BY accepted_at DESC, transcript_id DESC
                        LIMIT ?
                    ) recent
                    ORDER BY accepted_at ASC, transcript_id ASC
                    """, this::map, incidentId, limit);
        }
        Optional<StoredTranscript> cursor = findById(incidentId, cursorTranscriptId);
        if (cursor.isEmpty()) {
            return findReplayEvents(incidentId, null, limit);
        }
        return jdbcTemplate.query("""
                SELECT * FROM incident_phone_transcripts
                WHERE incident_id = ?
                  AND ((accepted_at > ?)
                       OR (transcript_id = ? AND review_revision > ?))
                ORDER BY accepted_at ASC, transcript_id ASC
                LIMIT ?
                """, this::map, incidentId, cursor.get().acceptedAt(), cursorTranscriptId,
                cursorRevision, limit);
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
        String reviewStatus = request.isFinal()
                ? PhoneTranscriptReviewStatus.FINAL_PENDING_REVIEW.name()
                : PhoneTranscriptReviewStatus.INTERIM.name();
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
        return findById(incidentId, transcriptId).orElseThrow();
    }

    @Override
    public StoredTranscript review(String incidentId, String transcriptId, String text,
                                   long expectedRevision, String reviewerUserId,
                                   String reviewerOrganizationId, String requestId,
                                   OffsetDateTime reviewedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE incident_phone_transcripts
                SET reviewed_text = ?, review_status = 'REVIEWED',
                    review_revision = review_revision + 1,
                    reviewed_by_user_id = ?, reviewed_by_organization_id = ?,
                    reviewed_at = ?, received_request_id = ?
                WHERE incident_id = ? AND transcript_id = ? AND is_final = TRUE
                  AND review_status = 'FINAL_PENDING_REVIEW'
                  AND review_revision = ?
                """, text, reviewerUserId, reviewerOrganizationId, reviewedAt, requestId,
                incidentId, transcriptId, expectedRevision);
        if (updated != 1) {
            StoredTranscript current = findById(incidentId, transcriptId)
                    .orElseThrow(() -> new BffContractException(404,
                            "PHONE_TRANSCRIPT_NOT_FOUND", "검토할 전화 전사를 찾을 수 없습니다.", false));
            if (!current.isFinal()) {
                throw new BffContractException(409, "PHONE_TRANSCRIPT_NOT_FINAL",
                        "실시간 초안은 검토 승인할 수 없습니다.", false);
            }
            throw new BffContractException(409, "PHONE_TRANSCRIPT_REVISION_CONFLICT",
                    "전화 전사가 이미 변경되었습니다. 최신 상태를 다시 확인하세요.", true);
        }
        return findById(incidentId, transcriptId).orElseThrow();
    }

    @Override
    public StoredTranscript markAnalyzed(String incidentId, String transcriptId,
                                         long expectedRevision, String analysisId,
                                         String requestId, OffsetDateTime analyzedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE incident_phone_transcripts
                SET review_status = 'ANALYZED',
                    analyzed_analysis_id = COALESCE(analyzed_analysis_id, ?),
                    analyzed_at = COALESCE(analyzed_at, ?), received_request_id = ?
                WHERE incident_id = ? AND transcript_id = ?
                  AND review_revision = ?
                  AND review_status IN ('REVIEWED', 'ANALYZED')
                """, analysisId, analyzedAt, requestId, incidentId, transcriptId,
                expectedRevision);
        if (updated != 1) {
            throw new BffContractException(409, "PHONE_TRANSCRIPT_REVISION_CONFLICT",
                    "분석 중 전화 전사 검토 상태가 변경되었습니다.", true);
        }
        return findById(incidentId, transcriptId).orElseThrow();
    }

    private List<StoredTranscript> query(String sql, Object... arguments) {
        return jdbcTemplate.query(sql, this::map, arguments);
    }

    private StoredTranscript map(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        String reviewedText = resultSet.getString("reviewed_text");
        return new StoredTranscript(
                resultSet.getString("transcript_id"),
                resultSet.getString("incident_id"),
                resultSet.getString("provider"),
                resultSet.getString("provider_call_id"),
                resultSet.getString("provider_event_id"),
                resultSet.getString("transcript_text"),
                reviewedText == null ? resultSet.getString("transcript_text") : reviewedText,
                resultSet.getString("language"),
                resultSet.getBoolean("is_final"),
                (Integer) resultSet.getObject("segment_index"),
                resultSet.getString("review_status"),
                resultSet.getLong("review_revision"),
                resultSet.getString("reviewed_by_user_id"),
                resultSet.getString("reviewed_by_organization_id"),
                resultSet.getObject("reviewed_at", OffsetDateTime.class),
                resultSet.getString("analyzed_analysis_id"),
                resultSet.getObject("analyzed_at", OffsetDateTime.class),
                resultSet.getObject("accepted_at", OffsetDateTime.class),
                resultSet.getString("received_request_id"));
    }

    private String transcriptId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        int separator = eventId.lastIndexOf(":r");
        return separator <= 0 ? null : eventId.substring(0, separator);
    }

    private long revision(String eventId) {
        int separator = eventId == null ? -1 : eventId.lastIndexOf(":r");
        if (separator <= 0) {
            return -1;
        }
        try {
            return Long.parseLong(eventId.substring(separator + 2));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
