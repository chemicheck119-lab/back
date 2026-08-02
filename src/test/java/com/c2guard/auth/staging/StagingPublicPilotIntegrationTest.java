package com.c2guard.auth.staging;

import com.c2guard.integration.model.ModelApiClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
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
                .andExpect(content().string(containsString("파일럿 시작")))
                .andExpect(content().string(containsString("대회·QA용 공개 파일럿")))
                .andExpect(content().string(not(containsString("name=\"userId\""))))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test
    void issuesTheRestrictedSignedSessionFromTheSameOrigin() throws Exception {
        MvcResult started = mockMvc.perform(post(PILOT_PATH)
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
                .andExpect(jsonPath("$.stationId").value("station-public-pilot"))
                .andExpect(jsonPath("$.roles[0]").value("RESPONDER"));
    }

    @Test
    void rejectsCrossSitePilotSessionIssuance() throws Exception {
        mockMvc.perform(post(PILOT_PATH)
                        .header(HttpHeaders.ORIGIN, "https://attacker.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        not(hasItem(containsString("CHEMICHECK119_SESSION=")))));
    }
}
