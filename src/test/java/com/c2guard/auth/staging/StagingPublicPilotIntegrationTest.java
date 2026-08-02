package com.c2guard.auth.staging;

import com.c2guard.integration.model.ModelApiClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "chemicheck119.staging-auth.enabled=true",
        "chemicheck119.staging-auth.public-pilot-enabled=true",
        "chemicheck119.staging-auth.callback-url=https://chemicheck119.site/auth/callback",
        "chemicheck119.staging-auth.user-id=public-pilot",
        "chemicheck119.staging-auth.station-id=station-public-pilot",
        "chemicheck119.staging-auth.station-display-name=케미체크119 파일럿 상황실",
        "chemicheck119.staging-auth.password=staging-test-password-at-least-16-bytes",
        "chemicheck119.staging-auth.roles=RESPONDER",
        "chemicheck119.staging-auth.incident-scopes=*"
})
@AutoConfigureMockMvc
class StagingPublicPilotIntegrationTest {

    private static final String PILOT_PATH = "/auth/staging/pilot";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void showsAPlainLanguagePilotPageWithoutCredentialFields() throws Exception {
        mockMvc.perform(get("/auth/staging/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("파일럿을 바로 시작하세요")))
                .andExpect(content().string(containsString("name=\"stationId\"")))
                .andExpect(content().string(containsString("서울 강남소방서")))
                .andExpect(content().string(containsString("선택한 소방서로 시작")))
                .andExpect(content().string(containsString("대회·QA용 공개 파일럿")))
                .andExpect(content().string(not(containsString("name=\"userId\""))))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test
    void publishesThePublicFireStationCatalogWithOfficialCoordinates() throws Exception {
        mockMvc.perform(get(PILOT_PATH + "/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion")
                        .value("chemicheck119-fire-station-catalog-v1"))
                .andExpect(jsonPath("$.sourceName")
                        .value("소방청_전국소방서 좌표현황(XY좌표)"))
                .andExpect(jsonPath("$.regions.length()").value(17))
                .andExpect(jsonPath("$.regions[0].regionName").value("서울"))
                .andExpect(jsonPath("$.regions[0].stations[0].stationId").value("nfa-0985"))
                .andExpect(jsonPath("$.regions[0].stations[0].latitude").value(37.5102929))
                .andExpect(jsonPath("$.regions[0].stations[0].longitude").value(127.06684));
    }

    @Test
    void issuesTheSelectedStationSessionFromTheSameOrigin() throws Exception {
        MvcResult started = mockMvc.perform(post(PILOT_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("stationId", "nfa-0985")
                        .header(HttpHeaders.ORIGIN, "https://chemicheck119.site"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("https://chemicheck119.site/auth/callback"))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("CHEMICHECK119_SESSION="))))
                .andReturn();
        Cookie session = started.getResponse().getCookie("CHEMICHECK119_SESSION");

        mockMvc.perform(get("/api/c2guard/v1/session").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("public-pilot"))
                .andExpect(jsonPath("$.stationId").value("nfa-0985"))
                .andExpect(jsonPath("$.stationDisplayName").value("서울 강남소방서"))
                .andExpect(jsonPath("$.stationLocation.address")
                        .value("서울특별시 강남구 테헤란로 629(삼성동)"))
                .andExpect(jsonPath("$.stationLocation.coordinateSource")
                        .value("NFA_PUBLIC_DATA"))
                .andExpect(jsonPath("$.roles[0]").value("RESPONDER"));
    }

    @Test
    void rejectsCrossSitePilotSessionIssuance() throws Exception {
        mockMvc.perform(post(PILOT_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("stationId", "nfa-0985")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        not(hasItem(containsString("CHEMICHECK119_SESSION=")))));
    }

    @Test
    void rejectsUnknownStationsWithoutIssuingASession() throws Exception {
        mockMvc.perform(post(PILOT_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("stationId", "station-attacker")
                        .header(HttpHeaders.ORIGIN, "https://chemicheck119.site"))
                .andExpect(status().isBadRequest())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        not(hasItem(containsString("CHEMICHECK119_SESSION=")))));
    }
}
