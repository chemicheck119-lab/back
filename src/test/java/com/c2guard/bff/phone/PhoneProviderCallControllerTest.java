package com.c2guard.bff.phone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "chemicheck119.phone-ingress.enabled=true",
        "chemicheck119.phone-ingress.token=test-phone-token"
})
@AutoConfigureMockMvc
@Transactional
class PhoneProviderCallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void providerClaimsExactlyOneWaitingSessionAndEndsIdempotently() throws Exception {
        insertWaitingSession("INC-PHONE-CLAIM-1", "USER-1");
        String startedAt = OffsetDateTime.now(ZoneOffset.UTC).toString();

        String startBody = """
                {
                  "provider": "clawops",
                  "callId": "CALL-CLAIM-1",
                  "eventId": "EVENT-START-1",
                  "occurredAt": "%s"
                }
                """.formatted(startedAt);

        mockMvc.perform(post("/api/c2guard/v1/phone-provider/calls/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Phone-Ingress-Token", "test-phone-token")
                        .header("X-Request-Id", "REQ-CALL-START-1")
                        .content(startBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("INC-PHONE-CLAIM-1"))
                .andExpect(jsonPath("$.callId").value("CALL-CLAIM-1"))
                .andExpect(jsonPath("$.status").value("IN_CALL"))
                .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(post("/api/c2guard/v1/phone-provider/calls/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Phone-Ingress-Token", "test-phone-token")
                        .content(startBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("INC-PHONE-CLAIM-1"))
                .andExpect(jsonPath("$.duplicate").value(true));

        mockMvc.perform(post("/api/c2guard/v1/incidents/INC-PHONE-CLAIM-1/phone-transcripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Phone-Ingress-Token", "test-phone-token")
                        .content("""
                                {
                                  "provider": "clawops",
                                  "callId": "CALL-CLAIM-1",
                                  "eventId": "clawops:utterance:test-1",
                                  "occurredAt": "%s",
                                  "text": "저장 탱크 주변에서 자극적인 냄새가 납니다.",
                                  "language": "ko",
                                  "isFinal": false,
                                  "segmentIndex": 1
                                }
                                """.formatted(OffsetDateTime.now(ZoneOffset.UTC))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("INC-PHONE-CLAIM-1"))
                .andExpect(jsonPath("$.callId").value("CALL-CLAIM-1"))
                .andExpect(jsonPath("$.reviewStatus").value("INTERIM"));

        Integer transcriptCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM incident_phone_transcripts
                WHERE incident_id = ? AND provider_call_id = ?
                """, Integer.class, "INC-PHONE-CLAIM-1", "CALL-CLAIM-1");
        org.assertj.core.api.Assertions.assertThat(transcriptCount).isEqualTo(1);

        String endBody = """
                {
                  "provider": "clawops",
                  "eventId": "EVENT-END-1",
                  "occurredAt": "%s",
                  "status": "completed"
                }
                """.formatted(OffsetDateTime.now(ZoneOffset.UTC).toString());

        mockMvc.perform(post("/api/c2guard/v1/phone-provider/calls/CALL-CLAIM-1/end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Phone-Ingress-Token", "test-phone-token")
                        .content(endBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(post("/api/c2guard/v1/phone-provider/calls/CALL-CLAIM-1/end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Phone-Ingress-Token", "test-phone-token")
                        .content(endBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void multipleWaitingSessionsFailClosedInsteadOfGuessingUser() throws Exception {
        insertWaitingSession("INC-PHONE-AMBIGUOUS-1", "USER-1");
        insertWaitingSession("INC-PHONE-AMBIGUOUS-2", "USER-2");

        mockMvc.perform(post("/api/c2guard/v1/phone-provider/calls/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Phone-Ingress-Token", "test-phone-token")
                        .content("""
                                {
                                  "provider": "clawops",
                                  "callId": "CALL-AMBIGUOUS",
                                  "eventId": "EVENT-AMBIGUOUS",
                                  "occurredAt": "%s"
                                }
                """.formatted(OffsetDateTime.now(ZoneOffset.UTC))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PHONE_SESSION_AMBIGUOUS"));
    }

    @Test
    void wrongIngressTokenIsRejectedBeforeClaim() throws Exception {
        insertWaitingSession("INC-PHONE-AUTH-1", "USER-1");

        mockMvc.perform(post("/api/c2guard/v1/phone-provider/calls/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Phone-Ingress-Token", "wrong-token")
                        .content("""
                                {
                                  "provider": "clawops",
                                  "callId": "CALL-AUTH",
                                  "eventId": "EVENT-AUTH",
                                  "occurredAt": "%s"
                                }
                """.formatted(OffsetDateTime.now(ZoneOffset.UTC))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("PHONE_INGRESS_UNAUTHORIZED"));
    }

    private void insertWaitingSession(String incidentId, String userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO incidents (incident_id, created_at, last_activity_at)
                VALUES (?, ?, ?)
                """, incidentId, now, now);
        jdbcTemplate.update("""
                INSERT INTO incident_phone_sessions (
                    incident_id, user_id, organization_id, station_display_name,
                    session_id, session_status, created_at
                ) VALUES (?, ?, 'STATION-1', '테스트 소방서', ?, 'WAITING_FOR_CALL', ?)
                """, incidentId, userId, "SESSION-" + incidentId, now);
    }
}
