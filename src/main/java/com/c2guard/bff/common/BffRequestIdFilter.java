package com.c2guard.bff.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BffRequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = BffRequestIdFilter.class.getName() + ".requestId";

    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9_.:-]{1,128}$");
    private static final Pattern INCIDENT_CHILD_PATH = Pattern.compile(
            "^/api/c2guard/v1/incidents/[^/]+/(confirmations|movement|record)$");
    private static final Pattern LEGACY_FACILITY_SUBSTANCES = Pattern.compile(
            "^/api/facilities/[^/]+/substances$");
    private static final Pattern INCIDENT_REPLAY_STREAM = Pattern.compile(
            "^/api/c2guard/v1/intake/replay-stream/[^/]+$");
    private static final Set<String> KNOWN_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");
    private static final Logger log = LoggerFactory.getLogger(BffRequestIdFilter.class);

    private final MeterRegistry meterRegistry;

    public BffRequestIdFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = normalize(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put("requestId", requestId);
        long startedNanos = System.nanoTime();
        boolean completed = false;
        try {
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            int status = completed ? response.getStatus() : effectiveFailureStatus(response.getStatus());
            String method = methodName(request.getMethod());
            String route = routeName(request.getRequestURI());
            String outcome = outcome(status);
            long durationNanos = System.nanoTime() - startedNanos;

            Timer.builder("chemicheck119.bff.request.duration")
                    .description("BFF API request duration")
                    .tags("method", method, "route", route, "outcome", outcome)
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
            if (status >= 400) {
                Counter.builder("chemicheck119.bff.request.errors")
                        .description("BFF API request errors")
                        .tags("method", method, "route", route, "outcome", outcome)
                        .register(meterRegistry)
                        .increment();
            }
            log.info("bff_http_request requestId={} method={} route={} status={} outcome={} durationMs={}",
                    requestId, method, route, status, outcome,
                    TimeUnit.NANOSECONDS.toMillis(durationNanos));
            MDC.remove("requestId");
        }
    }

    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof String requestId ? requestId : normalize(null);
    }

    static String normalize(String candidate) {
        if (candidate != null && VALID_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return "REQ-BFF-" + UUID.randomUUID();
    }

    static String routeName(String requestUri) {
        if (requestUri == null) {
            return "unmatched";
        }
        return switch (requestUri) {
            case "/api/c2guard/v1/incidents/analyze" -> "incidents.analyze";
            case "/api/c2guard/v1/substances/discover" -> "substances.discover";
            case "/api/c2guard/v1/session" -> "session.get";
            case "/api/c2guard/v1/logout" -> "session.logout";
            case "/api/incident-check" -> "legacy.incident-check";
            case "/api/compatibility/check" -> "legacy.compatibility-check";
            case "/api/facilities/search" -> "legacy.facilities-search";
            case "/api/c2guard/records" -> "legacy.records";
            default -> dynamicRouteName(requestUri);
        };
    }

    private static String dynamicRouteName(String requestUri) {
        var incidentMatcher = INCIDENT_CHILD_PATH.matcher(requestUri);
        if (incidentMatcher.matches()) {
            return "incidents." + incidentMatcher.group(1);
        }
        if (LEGACY_FACILITY_SUBSTANCES.matcher(requestUri).matches()) {
            return "legacy.facility-substances";
        }
        if (INCIDENT_REPLAY_STREAM.matcher(requestUri).matches()) {
            return "intake.replay-stream";
        }
        return "unmatched";
    }

    private static String methodName(String method) {
        String normalized = method == null ? "" : method.toUpperCase(Locale.ROOT);
        return KNOWN_METHODS.contains(normalized) ? normalized : "OTHER";
    }

    private static String outcome(int status) {
        if (status >= 500) {
            return "SERVER_ERROR";
        }
        if (status >= 400) {
            return "CLIENT_ERROR";
        }
        return "SUCCESS";
    }

    private static int effectiveFailureStatus(int responseStatus) {
        return responseStatus >= 400 ? responseStatus : 500;
    }
}
