package com.c2guard.security;

import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"chemicheck119.cost-admission.enabled=true",
    "chemicheck119.cost-admission.max-in-flight=2",
    "chemicheck119.cost-admission.per-principal-per-minute=3"})
@AutoConfigureMockMvc
class CostAdmissionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SignedSessionTokenService tokens;
    @Autowired ObjectMapper mapper;
    @Autowired CostAdmissionProperties admission;
    @MockBean ModelApiClient upstream;
    static final String PATH = "/api/c2guard/v1/substances/discover";
    static final String BODY = "{\"query\":\"synthetic\",\"topK\":3,\"evidenceTopK\":3}";
    Cookie cookie(String user) { return new Cookie("CHEMICHECK119_SESSION",
        tokens.issue(user, "synthetic-station", Set.of(BffRole.RESPONDER), Set.of("INC-OWN"))); }

    @Test void parallelBurstRejectsBeforeUpstreamAndRecoversWithoutAutomaticRetry() throws Exception {
        var fixture = mapper.readTree(Files.readString(Path.of("src/test/resources/fixtures/model/material_discovery_response.json")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture).put("query", "synthetic");
        var baselineEntered = new CountDownLatch(16);
        var baselineRelease = new CountDownLatch(1);
        var baselineCalls = new AtomicInteger();
        var baselinePool = Executors.newFixedThreadPool(16);
        when(upstream.discoverSubstances(any(), anyString())).thenAnswer(invocation -> {
            baselineCalls.incrementAndGet(); baselineEntered.countDown();
            assertTrue(baselineRelease.await(15, TimeUnit.SECONDS));
            return new ModelApiResponse(invocation.getArgument(1), fixture);
        });
        // Same route/input/concurrency/fake provider; only admission policy changes.
        admission.setEnabled(false);
        try {
            var baselineFutures = new ArrayList<Future<Integer>>();
            for (int n = 0; n < 16; n++) { final int i = n;
                baselineFutures.add(baselinePool.submit(() -> statusFor("baseline-" + i))); }
            assertTrue(baselineEntered.await(15, TimeUnit.SECONDS));
            baselineRelease.countDown();
            for (var future : baselineFutures) assertEquals(200, future.get(15, TimeUnit.SECONDS));
            assertEquals(16, baselineCalls.get());
        } finally { baselineRelease.countDown(); baselinePool.shutdownNow(); admission.setEnabled(true); }
        var entered = new CountDownLatch(2);
        var rejected = new CountDownLatch(14);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger(); var peak = new AtomicInteger(); var calls = new AtomicInteger();
        doAnswer(invocation -> {
            calls.incrementAndGet(); peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            entered.countDown();
            try { assertTrue(release.await(15, TimeUnit.SECONDS)); }
            finally { active.decrementAndGet(); }
            return new ModelApiResponse(invocation.getArgument(1), fixture);
        }).when(upstream).discoverSubstances(any(), anyString());
        var pool = Executors.newFixedThreadPool(16);
        var futures = new ArrayList<Future<Integer>>();
        try {
            // Hold two real BFF dispatches inside the local fake provider.
            for (int n = 0; n < 2; n++) { final int i = n;
                futures.add(pool.submit(() -> statusFor("burst-" + i))); }
            assertTrue(entered.await(15, TimeUnit.SECONDS));
            for (int n = 2; n < 16; n++) { final int i = n;
                futures.add(pool.submit(() -> { int status = statusFor("burst-" + i);
                    if (status == 429) rejected.countDown(); return status; })); }
            assertTrue(rejected.await(15, TimeUnit.SECONDS));
            release.countDown();
            var statuses = new ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get(15, TimeUnit.SECONDS));
            assertEquals(2, Collections.frequency(statuses, 200));
            assertEquals(14, Collections.frequency(statuses, 429));
            assertEquals(2, calls.get()); assertEquals(2, peak.get());
            assertEquals(200, statusFor("after-burst"));
            assertEquals(3, calls.get());
            assertEquals(16, baselineCalls.get());
            Files.createDirectories(Path.of("build/reports/cost-security"));
            mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("build/reports/cost-security/burst.json").toFile(),
                Map.of("scope", "LOCAL_MOCKMVC_FAKE_PROVIDER", "submitted_per_variant", 16, "accepted", 2,
                    "rejected_429", 14, "upstream_calls_during_burst", 2, "peak_upstream_concurrency", 2,
                    "recovery_status", 200, "real_model_or_phone_calls", 0,
                    "baseline_upstream_calls_during_burst", baselineCalls.get()));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void repeatedAuthenticatedCallsAreBoundedAndMissingOrForeignScopeNeverCallsProvider() throws Exception {
        when(upstream.discoverSubstances(any(), anyString())).thenThrow(new IllegalArgumentException("synthetic-provider-failure"));
        for (int n = 0; n < 3; n++) assertEquals(400, statusFor("repeat"));
        mvc.perform(post(PATH).cookie(cookie("repeat")).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
            .andExpect(jsonPath("$.error.code").value("CAPACITY_LIMITED"))
            .andExpect(jsonPath("$.error.retryable").value(false));
        verify(upstream, times(3)).discoverSubstances(any(), anyString());
        clearInvocations(upstream);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/c2guard/v1/incidents/INC-FOREIGN/phone-transcripts/stream").cookie(cookie("scope")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(upstream);
    }

    private int statusFor(String user) throws Exception {
        return mvc.perform(post(PATH).cookie(cookie(user)).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andReturn().getResponse().getStatus();
    }
}
