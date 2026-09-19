package com.c2guard.bff.record;

import com.c2guard.bff.confirmation.ConfirmationIdGenerator;
import com.c2guard.bff.incident.IncidentAnalysisSnapshotStore;
import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.security.BffRole;
import com.c2guard.security.SignedSessionTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static com.c2guard.security.BffTestSession.responder;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "대응 기록" 목록·상세 조회({@code GET /api/c2guard/v1/records[/​{recordId}]})의
 * 소속 범위 제한과 사고 접근권한 재검사를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecordQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignedSessionTokenService tokenService;

    @Autowired
    private IncidentAnalysisSnapshotStore analysisStore;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RecordIdGenerator recordIdGenerator;

    @MockBean
    private ConfirmationIdGenerator confirmationIdGenerator;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void listReturnsOnlyRecordsSavedByTheCallersOrganization() throws Exception {
        String incidentId = "INC-QUERY-LIST";
        String analysisId = "ANL-QUERY-LIST";
        seedAnalysis(incidentId, analysisId);
        when(recordIdGenerator.nextId()).thenReturn("REC-QUERY-LIST-MINE");
        saveRecord(incidentId, analysisId, responder(tokenService, incidentId));

        String otherIncidentId = "INC-QUERY-LIST-OTHER-ORG";
        String otherAnalysisId = "ANL-QUERY-LIST-OTHER-ORG";
        seedAnalysis(otherIncidentId, otherAnalysisId);
        when(recordIdGenerator.nextId()).thenReturn("REC-QUERY-LIST-OTHER-ORG");
        saveRecord(otherIncidentId, otherAnalysisId, otherOrganizationResponder(
                "responder-other-org", otherIncidentId));

        mockMvc.perform(get("/api/c2guard/v1/records")
                        .cookie(responder(tokenService, incidentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion")
                        .value("chemicheck119-dashboard-bff-v1"))
                .andExpect(jsonPath("$.records[?(@.recordId=='REC-QUERY-LIST-MINE')]")
                        .exists())
                .andExpect(jsonPath(
                        "$.records[?(@.recordId=='REC-QUERY-LIST-OTHER-ORG')]")
                        .doesNotExist());
    }

    @Test
    void detailReturnsConversationAndStructuredOutcomeWhenIncidentIsInScope()
            throws Exception {
        String incidentId = "INC-QUERY-DETAIL";
        String analysisId = "ANL-QUERY-DETAIL";
        seedAnalysisWithConflict(incidentId, analysisId);
        when(recordIdGenerator.nextId()).thenReturn("REC-QUERY-DETAIL");
        saveRecord(incidentId, analysisId, responder(tokenService, incidentId));

        mockMvc.perform(get("/api/c2guard/v1/records/REC-QUERY-DETAIL")
                        .cookie(responder(tokenService, incidentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value("REC-QUERY-DETAIL"))
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.facilityName").value("울산 화학공장"))
                .andExpect(jsonPath("$.briefApplicationStatus").value("APPLIED"))
                .andExpect(jsonPath("$.finalResponseOutcome")
                        .value("SPREAD_CONTAINED"))
                .andExpect(jsonPath("$.performedActions[0]").value("ZONE_CONTROL"))
                .andExpect(jsonPath("$.conflictRisk.riskLevelKo").value("높음"))
                .andExpect(jsonPath("$.conflictRisk.hazardCodes[0]").value("C"))
                .andExpect(jsonPath("$.messages", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"));
    }

    @Test
    void detailIsDeniedWhenTheCallerHasNoScopeForTheIncidentEvenInTheSameOrganization()
            throws Exception {
        String incidentId = "INC-QUERY-DETAIL-SCOPE";
        String analysisId = "ANL-QUERY-DETAIL-SCOPE";
        seedAnalysis(incidentId, analysisId);
        when(recordIdGenerator.nextId()).thenReturn("REC-QUERY-DETAIL-SCOPE");
        saveRecord(incidentId, analysisId, responder(tokenService, incidentId));

        mockMvc.perform(get("/api/c2guard/v1/records/REC-QUERY-DETAIL-SCOPE")
                        .cookie(responder(tokenService, "INC-QUERY-UNRELATED")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void detailReturns404ForAnUnknownRecordId() throws Exception {
        mockMvc.perform(get("/api/c2guard/v1/records/REC-QUERY-DOES-NOT-EXIST")
                        .cookie(responder(tokenService, "INC-QUERY-ANY")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECORD_NOT_FOUND"));
    }

    @Test
    void listAndDetailRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/c2guard/v1/records"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/c2guard/v1/records/REC-QUERY-ANY"))
                .andExpect(status().isUnauthorized());
    }

    private Cookie otherOrganizationResponder(String userId, String incidentId) {
        String token = tokenService.issue(userId, "other-fire-station",
                Set.of(BffRole.RESPONDER), Set.of(incidentId));
        return new Cookie("CHEMICHECK119_SESSION", token);
    }

    private void saveRecord(String incidentId, String analysisId, Cookie session)
            throws Exception {
        mockMvc.perform(post("/api/c2guard/v1/incidents/" + incidentId + "/record")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(analysisId)))
                .andExpect(status().isCreated());
    }

    private void seedAnalysis(String incidentId, String analysisId) {
        ObjectNode source = objectMapper.createObjectNode().put("analysisId", analysisId);
        analysisStore.save(incidentId, analysisId, "REQ-" + analysisId,
                source, source, source);
    }

    private void seedAnalysisWithConflict(String incidentId, String analysisId) {
        ObjectNode bff = objectMapper.createObjectNode();
        ObjectNode review = bff.putObject("conflictReview");
        review.put("executed", true);
        ObjectNode result = review.putObject("result");
        result.put("kind", "ORDINAL_SCREENING_RESULT");
        result.put("incidentCas", "7681-52-9");
        result.put("facilityCas", "7647-01-0");
        result.put("ruleId", "CAMEO-REACTIVE-GROUP-COMPATIBILITY-MATRIX");
        result.put("ruleVersion", "RUNTIME-MANIFEST-1");
        result.put("severity", "HIGH_RISK");
        result.put("riskLevel", "HIGH");
        result.put("riskLevelKo", "높음");
        result.put("briefText", "산성 물질과 접촉하면 독성 염소가스가 발생할 수 있습니다.");
        result.put("expertReviewed", false);
        result.put("humanConfirmationRequired", true);
        result.putArray("hazardCodes").add("C").add("T");
        result.putArray("gasProducts").add("Cl2");
        analysisStore.save(incidentId, analysisId, "REQ-" + analysisId,
                objectMapper.createObjectNode(), bff, objectMapper.createObjectNode(),
                java.util.Map.of());
    }

    private String request(String analysisId) {
        return """
                {
                  "conversationStartedAt": "2026-07-31T14:20:00+09:00",
                  "messages": [
                    {
                      "messageId": "MSG-0001",
                      "sequence": 1,
                      "role": "USER",
                      "text": "차아염소산나트륨 저장탱크 누출",
                      "createdAt": "2026-07-31T14:20:00+09:00",
                      "analysisId": null
                    },
                    {
                      "messageId": "MSG-0002",
                      "sequence": 2,
                      "role": "ASSISTANT",
                      "text": "현장 확인이 필요합니다.",
                      "createdAt": "2026-07-31T14:20:02+09:00",
                      "analysisId": "%s"
                    }
                  ],
                  "analysisIds": ["%s"],
                  "confirmationIds": [],
                  "outcomeReport": {
                    "facilityName": "울산 화학공장",
                    "facilityAddress": "울산광역시 남구 산업로 119",
                    "performedActions": ["ZONE_CONTROL", "LEAK_SOURCE_CONTROL"],
                    "briefApplicationStatus": "APPLIED",
                    "additionalFactors": ["ENCLOSED_SPACE"],
                    "finalResponseOutcome": "SPREAD_CONTAINED"
                  }
                }
                """.formatted(analysisId, analysisId);
    }
}
