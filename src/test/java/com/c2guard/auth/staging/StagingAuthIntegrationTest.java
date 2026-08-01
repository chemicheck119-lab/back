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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        "chemicheck119.staging-auth.callback-url=https://chemicheck119.site/auth/callback",
        "chemicheck119.staging-auth.user-id=responder-staging",
        "chemicheck119.staging-auth.station-id=station-seoul-119",
        "chemicheck119.staging-auth.station-display-name=서울 테스트 소방서",
        "chemicheck119.staging-auth.password=staging-test-password-at-least-16-bytes",
        "chemicheck119.staging-auth.roles=RESPONDER",
        "chemicheck119.staging-auth.incident-scopes=*"
})
@AutoConfigureMockMvc
class StagingAuthIntegrationTest {

    private static final String LOGIN_PATH = "/auth/staging/login";
    private static final Pattern CSRF = Pattern.compile(
            "name=\"csrf\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void issuesASecureSessionThenPublishesContextAndLogsOut() throws Exception {
        LoginPage page = loginPage();

        MvcResult authenticated = mockMvc.perform(post(LOGIN_PATH)
                        .cookie(page.csrfCookie())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("csrf", page.csrf())
                        .param("userId", "responder-staging")
                        .param("password", "staging-test-password-at-least-16-bytes"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("https://chemicheck119.site/auth/callback"))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("CHEMICHECK119_SESSION="))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("HttpOnly"))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("Secure"))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("SameSite=Lax"))))
                .andReturn();
        Cookie session = authenticated.getResponse().getCookie("CHEMICHECK119_SESSION");

        mockMvc.perform(get("/api/c2guard/v1/session")
                        .cookie(session)
                        .header("X-Request-Id", "REQ-SESSION-CONTEXT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion")
                        .value("chemicheck119-dashboard-bff-v1"))
                .andExpect(jsonPath("$.requestId").value("REQ-SESSION-CONTEXT"))
                .andExpect(jsonPath("$.userId").value("responder-staging"))
                .andExpect(jsonPath("$.stationId").value("station-seoul-119"))
                .andExpect(jsonPath("$.stationDisplayName")
                        .value("서울 테스트 소방서"))
                .andExpect(jsonPath("$.roles[0]").value("RESPONDER"))
                .andExpect(jsonPath("$.incidentScopes[0]").value("*"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        mockMvc.perform(post("/api/c2guard/v1/logout").cookie(session))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("CHEMICHECK119_SESSION="))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("Max-Age=0"))));
    }

    @Test
    void rejectsMissingCsrfWithoutIssuingASession() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("userId", "responder-staging")
                        .param("password", "staging-test-password-at-least-16-bytes")
                        .param("csrf", "attacker-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("로그인 페이지가 만료")))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        not(hasItem(containsString("CHEMICHECK119_SESSION=")))));
    }

    @Test
    void rejectsInvalidCredentialsWithoutReflectingThem() throws Exception {
        LoginPage page = loginPage();

        mockMvc.perform(post(LOGIN_PATH)
                        .cookie(page.csrfCookie())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("csrf", page.csrf())
                        .param("userId", "attacker@example.test")
                        .param("password", "do-not-reflect-this-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("계정 또는 비밀번호")))
                .andExpect(content().string(not(containsString(
                        "do-not-reflect-this-password"))))
                .andExpect(content().string(not(containsString(
                        "attacker@example.test"))));
    }

    private LoginPage loginPage() throws Exception {
        MvcResult result = mockMvc.perform(get(LOGIN_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("form-action 'self'")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")))
                .andReturn();
        Matcher matcher = CSRF.matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new AssertionError("CSRF field is missing");
        }
        Cookie cookie = result.getResponse().getCookie(
                "CHEMICHECK119_STAGING_AUTH_CSRF");
        return new LoginPage(matcher.group(1), cookie);
    }

    private record LoginPage(String csrf, Cookie csrfCookie) {
    }
}
