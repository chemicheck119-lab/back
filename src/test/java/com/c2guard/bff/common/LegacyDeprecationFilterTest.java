package com.c2guard.bff.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyDeprecationFilterTest {

    private final LegacyDeprecationFilter filter = new LegacyDeprecationFilter();

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/incident-check",
            "/api/compatibility/check",
            "/api/facilities/search",
            "/api/facilities/예시/substances",
            "/api/c2guard/records"
    })
    void addsDeprecationMetadataToImplementedLegacyRoutes(String path) throws Exception {
        MockHttpServletResponse response = filter(path);

        assertEquals("true", response.getHeader("Deprecation"));
        assertEquals(LegacyDeprecationFilter.DEPRECATION_LINK,
                response.getHeader("Link"));
        assertNull(response.getHeader("Sunset"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/c2guard/v1/incidents/analyze",
            "/api/c2guard/v1/substances/discover",
            "/actuator/health/liveness"
    })
    void doesNotMarkBffOrHealthRoutesAsDeprecated(String path) throws Exception {
        MockHttpServletResponse response = filter(path);

        assertNull(response.getHeader("Deprecation"));
        assertNull(response.getHeader("Link"));
    }

    private MockHttpServletResponse filter(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
