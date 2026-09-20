package com.c2guard.bff.phone;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.security.BffRole;
import com.c2guard.security.BffUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PhoneTranscriptReviewIntegrationTest {

    private static final String INCIDENT_ID = "INC-PHONE-REVIEW";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PhoneTranscriptStore store;

    @Autowired
    private PhoneTranscriptReviewService reviewService;

    @BeforeEach
    void registerIncident() {
        OffsetDateTime now = Instant.parse("2026-09-20T08:00:00Z").atOffset(ZoneOffset.UTC);
        jdbcTemplate.update("INSERT INTO incidents (incident_id, created_at, last_activity_at) VALUES (?, ?, ?)",
                INCIDENT_ID, now, now);
    }

    @Test
    void finalTranscriptMustBeReviewedWithAnExactRevision() {
        PhoneTranscriptStore.StoredTranscript pending = save("TRX-FINAL", "EVENT-FINAL", true);

        PhoneTranscriptEvent reviewed = reviewService.review(INCIDENT_ID, pending.transcriptId(),
                new PhoneTranscriptReviewRequest("염소 누출로 수정", 0), principal(), "REQ-REVIEW");

        assertThat(reviewed.reviewStatus()).isEqualTo("REVIEWED");
        assertThat(reviewed.revision()).isEqualTo(1);
        assertThat(reviewed.text()).isEqualTo("염소 누출로 수정");
        assertThat(reviewed.reviewedBy()).isEqualTo("reviewer-1");
        assertThat(store.findReplayEvents(INCIDENT_ID, pending.streamEventId(), 100))
                .extracting(PhoneTranscriptStore.StoredTranscript::streamEventId)
                .containsExactly("TRX-FINAL:r1");

        assertThatThrownBy(() -> reviewService.review(INCIDENT_ID, pending.transcriptId(),
                new PhoneTranscriptReviewRequest("다시 덮어쓰기", 0), principal(), "REQ-STALE"))
                .isInstanceOf(BffContractException.class)
                .hasMessageContaining("이미 변경");
    }

    @Test
    void interimTranscriptCannotBeApproved() {
        PhoneTranscriptStore.StoredTranscript interim = save("TRX-INTERIM", "EVENT-INTERIM", false);

        assertThatThrownBy(() -> reviewService.review(INCIDENT_ID, interim.transcriptId(),
                new PhoneTranscriptReviewRequest("승인 시도", 0), principal(), "REQ-INTERIM"))
                .isInstanceOf(BffContractException.class)
                .hasMessageContaining("실시간 초안");
    }

    private PhoneTranscriptStore.StoredTranscript save(String transcriptId, String eventId,
                                                        boolean isFinal) {
        OffsetDateTime occurredAt = Instant.parse("2026-09-20T08:00:01Z")
                .atOffset(ZoneOffset.UTC);
        return store.save(INCIDENT_ID, new PhoneTranscriptIngressRequest(
                        "clawops", "CALL-1", eventId, occurredAt,
                        "염소 누출 의심", "ko", isFinal, isFinal ? 2 : 1),
                transcriptId, "REQ-INGRESS-" + eventId, occurredAt);
    }

    private BffUserPrincipal principal() {
        Instant issuedAt = Instant.parse("2026-09-20T08:00:00Z");
        return new BffUserPrincipal("reviewer-1", "station-1", "화성소방서",
                Set.of(BffRole.RESPONDER), Set.of(INCIDENT_ID), "session-1",
                issuedAt, issuedAt.plusSeconds(3600));
    }
}
