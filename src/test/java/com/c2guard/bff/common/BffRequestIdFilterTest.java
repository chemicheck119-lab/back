package com.c2guard.bff.common;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BffRequestIdFilterTest {

    @Test
    void recordsBoundedRouteMetricsAndPropagatesRequestId() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BffRequestIdFilter filter = new BffRequestIdFilter(registry);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/c2guard/v1/incidents/INC-SECRET-123/confirmations");
        request.addHeader(BffRequestIdFilter.HEADER, "REQ-FE-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(422));

        assertEquals("REQ-FE-123", response.getHeader(BffRequestIdFilter.HEADER));
        assertEquals("REQ-FE-123", request.getAttribute(BffRequestIdFilter.ATTRIBUTE));
        assertEquals(1.0, registry.get("chemicheck119.bff.request.duration")
                .tags("method", "POST", "route", "incidents.confirmations",
                        "outcome", "CLIENT_ERROR")
                .timer().count());
        assertEquals(1.0, registry.get("chemicheck119.bff.request.errors")
                .tags("method", "POST", "route", "incidents.confirmations",
                        "outcome", "CLIENT_ERROR")
                .counter().count());
        assertFalse(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .anyMatch(tag -> tag.getValue().contains("INC-SECRET-123")));
    }

    @Test
    void generatedRequestIdAndUnmatchedRouteDoNotExposeRawPath() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BffRequestIdFilter filter = new BffRequestIdFilter(registry);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "CUSTOM", "/api/private/user-provided-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        String requestId = response.getHeader(BffRequestIdFilter.HEADER);
        assertNotNull(requestId);
        assertTrue(requestId.startsWith("REQ-BFF-"));
        assertEquals(1.0, registry.get("chemicheck119.bff.request.duration")
                .tags("method", "OTHER", "route", "unmatched", "outcome", "SUCCESS")
                .timer().count());
        assertFalse(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .anyMatch(tag -> tag.getValue().contains("user-provided-value")));
    }

    @Test
    void routeClassifierCoversEveryCurrentBffContractWithoutIncidentIds() {
        assertEquals("incidents.analyze",
                BffRequestIdFilter.routeName("/api/c2guard/v1/incidents/analyze"));
        assertEquals("substances.discover",
                BffRequestIdFilter.routeName("/api/c2guard/v1/substances/discover"));
        assertEquals("incidents.movement",
                BffRequestIdFilter.routeName("/api/c2guard/v1/incidents/INC-1/movement"));
        assertEquals("incidents.record",
                BffRequestIdFilter.routeName("/api/c2guard/v1/incidents/INC-1/record"));
        assertEquals("incidents.transcriptions",
                BffRequestIdFilter.routeName(
                        "/api/c2guard/v1/incidents/INC-SECRET/transcriptions"));
        assertEquals("session.get",
                BffRequestIdFilter.routeName("/api/c2guard/v1/session"));
        assertEquals("session.logout",
                BffRequestIdFilter.routeName("/api/c2guard/v1/logout"));
        assertEquals("transcriptions.create",
                BffRequestIdFilter.routeName("/api/c2guard/v1/transcriptions"));
        assertEquals("intake.replay-stream",
                BffRequestIdFilter.routeName(
                        "/api/c2guard/v1/intake/replay-stream/CONTEST-LIVE-CHEMICAL-001"));
        assertEquals("intake.replay-confirmation",
                BffRequestIdFilter.routeName(
                        "/api/c2guard/v1/intake/replays/INC-PUBLIC-1/confirmations/INCIDENT"));
    }
}
