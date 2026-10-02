package com.c2guard.bff.phone;

import com.c2guard.security.SignedSessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.c2guard.security.BffTestSession.responder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "chemicheck119.phone-ingress.enabled=true")
@AutoConfigureMockMvc
@Transactional
class PhoneSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignedSessionTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void authenticatedOperatorCreatesServerOwnedIncidentSession() throws Exception {
        String response = mockMvc.perform(post("/api/c2guard/v1/phone-sessions")
                        .cookie(responder(tokenService, "*"))
                        .header("X-Request-Id", "REQ-PHONE-SESSION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("REQ-PHONE-SESSION"))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_CALL"))
                .andExpect(jsonPath("$.incidentId").value(
                        org.hamcrest.Matchers.startsWith("INC-PHONE-")))
                .andReturn().getResponse().getContentAsString();

        String incidentId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).path("incidentId").asText();
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM incident_phone_sessions
                WHERE incident_id = ? AND session_status = 'WAITING_FOR_CALL'
                """, Integer.class, incidentId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void repeatedPreparationReusesTheSameWaitingSessionForThatLoginSession() throws Exception {
        var cookie = responder(tokenService, "*");
        String first = mockMvc.perform(post("/api/c2guard/v1/phone-sessions")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/c2guard/v1/phone-sessions")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String firstIncidentId = mapper.readTree(first).path("incidentId").asText();
        String secondIncidentId = mapper.readTree(second).path("incidentId").asText();
        assertThat(secondIncidentId).isEqualTo(firstIncidentId);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM incident_phone_sessions
                WHERE incident_id = ? AND session_status = 'WAITING_FOR_CALL'
                """, Integer.class, firstIncidentId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void expiredWaitingSessionIsNotReturnedAsReadyAgain() throws Exception {
        var cookie = responder(tokenService, "*");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String first = mockMvc.perform(post("/api/c2guard/v1/phone-sessions").cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String firstId = mapper.readTree(first).path("incidentId").asText();
        jdbcTemplate.update("UPDATE incident_phone_sessions SET waiting_expires_at = ? WHERE incident_id = ?",
                java.time.OffsetDateTime.parse("2000-01-01T00:00:00Z"), firstId);
        String second = mockMvc.perform(post("/api/c2guard/v1/phone-sessions").cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(second).path("incidentId").asText()).isNotEqualTo(firstId);
        // Keep the old row for audit; do not extend its claim window.
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM incident_phone_sessions WHERE incident_id = ?",
                Integer.class, firstId)).isEqualTo(1);
    }

    @Test
    void heartbeatKeepsSameIncidentReadyWithoutRewritingCreationAudit() throws Exception {
        var cookie = responder(tokenService, "*");
        String id = create(cookie);
        var oldCreation = java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z");
        jdbcTemplate.update("UPDATE incident_phone_sessions SET created_at = ? WHERE incident_id = ?", oldCreation, id);
        mockMvc.perform(post("/api/c2guard/v1/phone-sessions/" + id + "/heartbeat").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.incidentId").value(id))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_CALL"))
                .andExpect(jsonPath("$.waitingExpiresAt").isNotEmpty());
        assertThat(jdbcTemplate.queryForObject("SELECT created_at FROM incident_phone_sessions WHERE incident_id = ?",
                java.time.OffsetDateTime.class, id).toInstant()).isEqualTo(oldCreation.toInstant());
    }

    @Test
    void expiredOrCanceledLeaseIsNeverResurrectedByHeartbeat() throws Exception {
        var cookie = responder(tokenService, "*");
        String id = create(cookie);
        jdbcTemplate.update("UPDATE incident_phone_sessions SET waiting_expires_at = ? WHERE incident_id = ?",
                java.time.OffsetDateTime.parse("2000-01-01T00:00:00Z"), id);
        mockMvc.perform(post("/api/c2guard/v1/phone-sessions/" + id + "/heartbeat").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
        String next = create(cookie);
        assertThat(next).isNotEqualTo(id);
        mockMvc.perform(delete("/api/c2guard/v1/phone-sessions/" + next).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED"));
        mockMvc.perform(post("/api/c2guard/v1/phone-sessions/" + next + "/heartbeat").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void anotherLoginCannotInspectRenewCancelOrCreateAmbiguousWaitingSession() throws Exception {
        var owner = responder(tokenService, "*");
        String id = create(owner);
        // A different login token gets a different server session ID.
        var other = responder(tokenService, "*");
        mockMvc.perform(get("/api/c2guard/v1/phone-sessions/" + id).cookie(other)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/c2guard/v1/phone-sessions/" + id + "/heartbeat").cookie(other)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/c2guard/v1/phone-sessions/" + id).cookie(other)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/c2guard/v1/phone-sessions").cookie(other))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PHONE_DESK_BUSY"));
    }

    @Test
    void activeCallIsRecoveredAndCannotBeCanceledOrMadeWaiting() throws Exception {
        var cookie = responder(tokenService, "*");
        String id = create(cookie);
        jdbcTemplate.update("UPDATE incident_phone_sessions SET session_status = 'IN_CALL' WHERE incident_id = ?", id);
        assertThat(create(cookie)).isEqualTo(id);
        mockMvc.perform(post("/api/c2guard/v1/phone-sessions/" + id + "/heartbeat").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_CALL"));
        mockMvc.perform(delete("/api/c2guard/v1/phone-sessions/" + id).cookie(cookie))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PHONE_CALL_IN_PROGRESS"));
    }

    private String create(jakarta.servlet.http.Cookie cookie) throws Exception {
        var result = mockMvc.perform(post("/api/c2guard/v1/phone-sessions").cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(result).path("incidentId").asText();
    }
}
