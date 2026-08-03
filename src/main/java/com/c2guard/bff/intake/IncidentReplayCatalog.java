package com.c2guard.bff.intake;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.station.FireStationCatalog;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class IncidentReplayCatalog {

    public static final String CONTEST_SCENARIO_ID = "CONTEST-LIVE-CHEMICAL-001";
    private static final String SCHEMA_VERSION = "chemicheck119-incident-envelope-v1";
    private static final String SOURCE_SCHEMA_VERSION = "public-replay-v1";

    private final Clock clock;

    public IncidentReplayCatalog(Clock clock) {
        this.clock = clock;
    }

    public IncidentEnvelope create(String scenarioId, String requestId) {
        return create(scenarioId, requestId, null);
    }

    public IncidentEnvelope create(String scenarioId, String requestId,
                                   FireStationCatalog.Station station) {
        String normalized = scenarioId == null ? "" : scenarioId.trim().toUpperCase(Locale.ROOT);
        if (!CONTEST_SCENARIO_ID.equals(normalized)) {
            throw new BffContractException(404, "REPLAY_SCENARIO_NOT_FOUND",
                    "요청한 공개 합성 지령 시나리오를 찾을 수 없습니다.", false);
        }

        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        String replaySuffix = receivedAt.toString().replaceAll("[^0-9]", "")
                + "-" + UUID.randomUUID();
        String sourceEventId = "SYNTHETIC-DISPATCH-" + replaySuffix;
        String stationId = station == null ? "STATION-PUBLIC-DEMO" : station.stationId();
        String stationDisplayName = station == null
                ? "공개 시연 소방서" : station.stationDisplayName();
        String facilityName = station == null
                ? "공개 합성 사업장" : station.region() + " 공개 합성 화학취급시설";
        String addressText = station == null
                ? "경기도 화성시 팔탄면"
                : station.stationDisplayName() + " 관할 공개 합성 사고지점";
        IncidentLocation location = station == null
                ? new IncidentLocation(new BigDecimal("37.159000"),
                new BigDecimal("126.904000"))
                : stationScopedIncidentLocation(station);
        List<IncidentDatasetReference> datasetReferences = station == null
                ? List.of(bigDataReference())
                : List.of(bigDataReference(), new IncidentDatasetReference(
                FireStationCatalog.SOURCE_NAME,
                FireStationCatalog.SOURCE_URL,
                "선택 소방서 공개 좌표를 합성 출동 기준점으로 사용"));
        return new IncidentEnvelope(
                SCHEMA_VERSION,
                "INC-PUBLIC-" + replaySuffix,
                sourceEventId,
                "PUBLIC-REPLAY:" + normalized + ":" + replaySuffix,
                receivedAt,
                receivedAt.minusSeconds(45),
                stationId,
                stationDisplayName,
                facilityName,
                addressText,
                location,
                "차아염소산나트륨 저장탱크 누출 의심, 인접 저장고에 염산 표기",
                IncidentSourceType.SYNTHETIC_DISPATCH_REPLAY,
                IncidentDataClassification.PUBLIC_SYNTHETIC,
                "CHEMICHECK119_PUBLIC_REPLAY",
                SOURCE_SCHEMA_VERSION,
                requestId,
                false,
                "실제 119 신고가 아닌 개인정보 없는 공개 합성 지령입니다. 배포된 BE와 AI 처리 경로를 검증합니다.",
                datasetReferences);
    }

    private IncidentLocation stationScopedIncidentLocation(FireStationCatalog.Station station) {
        double latitude = station.latitude();
        double longitude = station.longitude();
        double longitudeScale = Math.cos(Math.toRadians(latitude));
        double latitudeVector = 36.5 - latitude;
        double longitudeVector = (127.8 - longitude) * longitudeScale;
        double vectorLength = Math.hypot(latitudeVector, longitudeVector);
        double offsetDegrees = 0.035;
        if (vectorLength < 0.000001) {
            latitudeVector = offsetDegrees;
            longitudeVector = 0;
            vectorLength = offsetDegrees;
        }
        double incidentLatitude = latitude
                + latitudeVector / vectorLength * offsetDegrees;
        double incidentLongitude = longitude
                + longitudeVector / vectorLength * offsetDegrees / longitudeScale;
        return new IncidentLocation(coordinate(incidentLatitude), coordinate(incidentLongitude));
    }

    private BigDecimal coordinate(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private IncidentDatasetReference bigDataReference() {
        return new IncidentDatasetReference(
                "소방안전 빅데이터 플랫폼",
                "https://bigdata-119.kr/",
                "공개 소방 데이터 구조와 대회 활용 맥락 참고");
    }
}
