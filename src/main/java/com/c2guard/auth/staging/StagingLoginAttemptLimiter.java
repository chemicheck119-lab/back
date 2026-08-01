package com.c2guard.auth.staging;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
class StagingLoginAttemptLimiter {

    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final StagingAuthProperties properties;
    private final Clock clock;

    StagingLoginAttemptLimiter(StagingAuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    boolean blocked(String key) {
        AttemptState state = attempts.get(key);
        if (state == null) {
            return false;
        }
        if (!state.firstFailure().plus(properties.getLockDuration())
                .isAfter(clock.instant())) {
            attempts.remove(key, state);
            return false;
        }
        return state.failures() >= properties.getMaxFailedAttempts();
    }

    void failed(String key) {
        Instant now = clock.instant();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || !current.firstFailure()
                    .plus(properties.getLockDuration()).isAfter(now)) {
                return new AttemptState(1, now);
            }
            return new AttemptState(current.failures() + 1, current.firstFailure());
        });
    }

    void succeeded(String key) {
        attempts.remove(key);
    }

    private record AttemptState(int failures, Instant firstFailure) {
    }
}
