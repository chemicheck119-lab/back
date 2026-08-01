package com.c2guard.bff.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record IncidentAnalyzeRequest(
        @Size(min = 1, max = 128) String incidentId,
        @NotBlank @Size(max = 4000) String text,
        InputType inputType,
        OffsetDateTime occurredAt,
        @Valid IncidentLocation location,
        @Valid OperationsContext operationsContext,
        @Size(max = 20) List<@NotBlank @Size(max = 120) String> plannedActions,
        @Min(1) @Max(10) Integer evidenceTopK
) {
    public IncidentAnalyzeRequest {
        inputType = inputType == null ? InputType.MANUAL_TEXT : inputType;
        plannedActions = plannedActions == null ? List.of() : List.copyOf(plannedActions);
        evidenceTopK = evidenceTopK == null ? 5 : evidenceTopK;
    }

    public enum InputType {
        MANUAL_TEXT, DISPATCH_TEXT, VOICE_TRANSCRIPT, STRUCTURED_FORM
    }

    public enum CoordinateSource {
        DISPATCH_SYSTEM, GEOCODING_PROVIDER, RESPONDER_OBSERVATION, MANUAL_ENTRY, DEMO_FIXTURE
    }

    public enum JourneyState {
        DISPATCHED, EN_ROUTE, ARRIVED, ON_SCENE
    }

    public enum PositionSource {
        VEHICLE_GPS, MDT_DEVICE_GPS, MANUAL_DISPATCH, DEMO_SIMULATION
    }

    public record IncidentLocation(
            @Size(max = 200) String facilityName,
            @Size(max = 300) String address,
            @Size(max = 80) String province,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            CoordinateSource coordinateSource,
            @Size(max = 80) String geocodingProvider,
            OffsetDateTime resolvedAt
    ) {
        @AssertTrue(message = "latitude와 longitude는 함께 입력해야 합니다.")
        @JsonIgnore
        public boolean isCoordinatePairValid() {
            return (latitude == null) == (longitude == null);
        }

        @AssertTrue(message = "좌표 출처와 확인 시각에는 좌표가 필요합니다.")
        @JsonIgnore
        public boolean isCoordinateMetadataValid() {
            return (coordinateSource == null && resolvedAt == null) || latitude != null;
        }

        @AssertTrue(message = "geocodingProvider는 GEOCODING_PROVIDER 좌표에만 허용됩니다.")
        @JsonIgnore
        public boolean isGeocodingProviderValid() {
            return geocodingProvider == null || coordinateSource == CoordinateSource.GEOCODING_PROVIDER;
        }
    }

    public record OperationsContext(
            @Size(max = 160) String dispatchStationName,
            @Valid ResponderPosition responderPosition,
            JsonNode route,
            JourneyState journeyState
    ) {
        public OperationsContext {
            journeyState = journeyState == null ? JourneyState.DISPATCHED : journeyState;
        }
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
