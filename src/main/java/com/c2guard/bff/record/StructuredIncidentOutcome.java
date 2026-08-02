package com.c2guard.bff.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 화재조사·사후분석에 필요한 현장 결과 입력입니다.
 * 사고물질과 RuleEngine 결과는 클라이언트 입력을 신뢰하지 않고 서버 snapshot에서 추출합니다.
 */
public record StructuredIncidentOutcome(
        @NotBlank @Size(max = 200) String facilityName,
        @Size(max = 300) String facilityAddress,
        @NotNull @Size(min = 1, max = 20)
        List<@NotNull PerformedAction> performedActions,
        @NotNull BriefApplicationStatus briefApplicationStatus,
        @NotNull @Size(max = 20)
        List<@NotNull AdditionalFactor> additionalFactors,
        @NotNull FinalResponseOutcome finalResponseOutcome
) {
    public enum PerformedAction {
        ZONE_CONTROL,
        EVACUATION,
        LEAK_SOURCE_CONTROL,
        ADSORPTION_OR_RECOVERY,
        WATER_SPRAY_OR_DILUTION,
        VENTILATION,
        DECONTAMINATION,
        RESCUE_OR_EMS,
        OTHER
    }

    public enum BriefApplicationStatus {
        NOT_REVIEWED,
        REVIEWED_NOT_APPLIED,
        PARTIALLY_APPLIED,
        APPLIED
    }

    public enum AdditionalFactor {
        ACTUAL_MIXING_CONFIRMED,
        ENCLOSED_SPACE,
        HEAT_OR_PRESSURE,
        DRAIN_OR_WATERWAY_CONNECTION,
        LABEL_OR_MSDS_MISMATCH,
        ADDITIONAL_SUBSTANCE_FOUND,
        CASUALTY_OR_EXPOSURE,
        WEATHER_INFLUENCE,
        OTHER
    }

    public enum FinalResponseOutcome {
        LEAK_STOPPED,
        SPREAD_CONTAINED,
        EVACUATION_COMPLETED,
        MATERIAL_RECOVERED,
        TRANSFERRED_TO_SPECIALIST,
        FALSE_ALARM,
        MONITORING_CONTINUES,
        OTHER
    }
}
