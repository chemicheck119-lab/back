package com.c2guard.integration.model;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

final class ModelApiTelemetry {

    private final MeterRegistry meterRegistry;

    ModelApiTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    static ModelApiTelemetry noop() {
        return new ModelApiTelemetry(null);
    }

    void recordSuccess(String path, int attempts, long durationNanos) {
        record(path, "SUCCESS", attempts, durationNanos, false);
    }

    void recordFailure(String path, ModelApiErrorKind kind, int attempts, long durationNanos) {
        record(path, kind.name(), attempts, durationNanos, true);
    }

    private void record(String path, String outcome, int attempts,
                        long durationNanos, boolean failed) {
        if (meterRegistry == null) {
            return;
        }
        String operation = operation(path);
        String attemptCount = attemptCount(attempts);
        Timer.builder("chemicheck119.model.api.duration")
                .description("Model API call duration including retries")
                .tags("operation", operation, "outcome", outcome, "attempts", attemptCount)
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        if (failed) {
            Counter.builder("chemicheck119.model.api.errors")
                    .description("Model API call errors after retries")
                    .tags("operation", operation, "outcome", outcome,
                            "attempts", attemptCount)
                    .register(meterRegistry)
                    .increment();
        }
    }

    static String operation(String path) {
        if (path == null) {
            return "unmatched";
        }
        return switch (path) {
            case "/health/live" -> "health.live";
            case "/health/ready" -> "health.ready";
            case "/api/v1/meta" -> "meta";
            case "/api/v1/substances/resolve" -> "substances.resolve";
            case "/api/v1/substances/discover" -> "substances.discover";
            case "/api/v1/evidence/search" -> "evidence.search";
            case "/api/v1/facilities/candidates" -> "facilities.candidates";
            case "/api/v1/conflicts/review" -> "conflicts.review";
            case "/api/v1/incidents/analyze" -> "incidents.analyze";
            case "/api/v1/agents/incidents/step" -> "incidents.agent-step";
            default -> "unmatched";
        };
    }

    private static String attemptCount(int attempts) {
        return switch (attempts) {
            case 0 -> "0";
            case 1 -> "1";
            case 2 -> "2";
            default -> "3+";
        };
    }
}
