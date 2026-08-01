package com.c2guard.bff.movement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record MovementUpdateRequest(
        @NotNull @Valid ResponderPosition responderPosition,
        @NotNull JourneyState journeyState,
        @NotNull @Min(1) Long clientSequence
) {
    public enum JourneyState {
        DISPATCHED,
        EN_ROUTE,
        ARRIVED,
        ON_SCENE
    }

    public enum PositionSource {
        VEHICLE_GPS,
        MDT_DEVICE_GPS,
        MANUAL_DISPATCH,
        DEMO_SIMULATION
    }

    public record ResponderPosition(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull OffsetDateTime observedAt,
            @NotNull PositionSource source,
            @DecimalMin("0") @DecimalMax("5000") Double accuracyM
    ) {
    }
}
