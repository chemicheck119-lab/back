package com.c2guard.bff.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LegacyDeprecationFilter extends OncePerRequestFilter {

    static final String DEPRECATION_HEADER = "Deprecation";
    static final String DEPRECATION_LINK =
            "<https://github.com/chemicheck119/BE_Repository/blob/develop/"
                    + "docs/BFF_V1_API_CONTRACT.md>; rel=\"deprecation\"";

    private static final Set<String> EXACT_PATHS = Set.of(
            "/api/incident-check",
            "/api/compatibility/check",
            "/api/c2guard/records"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !EXACT_PATHS.contains(path) && !path.startsWith("/api/facilities/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(DEPRECATION_HEADER, "true");
        response.setHeader("Link", DEPRECATION_LINK);
        filterChain.doFilter(request, response);
    }
}
