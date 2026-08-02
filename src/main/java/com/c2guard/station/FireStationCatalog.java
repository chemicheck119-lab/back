package com.c2guard.station;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class FireStationCatalog {

    public static final String SCHEMA_VERSION = "chemicheck119-fire-station-catalog-v1";
    public static final String SOURCE_NAME = "소방청_전국소방서 좌표현황(XY좌표)";
    public static final String SOURCE_URL = "https://www.data.go.kr/data/15138232/fileData.do";
    private static final String RESOURCE_PATH = "data/nfa-fire-stations-20240901.csv";
    private static final List<String> REGION_ORDER = List.of(
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");

    private Map<String, Station> stationsById = Map.of();
    private List<Region> regions = List.of();
    private LocalDate sourceDate;

    @PostConstruct
    void load() {
        Map<String, Station> loadedStations = new LinkedHashMap<>();
        Map<String, List<Station>> byRegion = new LinkedHashMap<>();
        REGION_ORDER.forEach(region -> byRegion.put(region, new ArrayList<>()));

        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!"station_id,region,station_name,address,latitude,longitude,phone,source_date"
                    .equals(header)) {
                throw new IllegalStateException("소방서 카탈로그 헤더가 올바르지 않습니다.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = parseCsvLine(line);
                if (fields.size() != 8) {
                    throw new IllegalStateException("소방서 카탈로그 행이 올바르지 않습니다.");
                }
                LocalDate rowSourceDate = LocalDate.parse(fields.get(7));
                if (sourceDate == null) {
                    sourceDate = rowSourceDate;
                } else if (!sourceDate.equals(rowSourceDate)) {
                    throw new IllegalStateException("소방서 카탈로그 기준일이 일치하지 않습니다.");
                }
                Station station = new Station(fields.get(0), fields.get(1), fields.get(2),
                        fields.get(3), parseLatitude(fields.get(4)),
                        parseLongitude(fields.get(5)), fields.get(6), rowSourceDate);
                if (!byRegion.containsKey(station.region())
                        || loadedStations.putIfAbsent(station.stationId(), station) != null) {
                    throw new IllegalStateException("소방서 카탈로그 식별자가 올바르지 않습니다.");
                }
                byRegion.get(station.region()).add(station);
            }
        } catch (IOException error) {
            throw new IllegalStateException("소방서 카탈로그를 읽지 못했습니다.", error);
        }

        if (loadedStations.isEmpty() || sourceDate == null) {
            throw new IllegalStateException("소방서 카탈로그가 비어 있습니다.");
        }
        stationsById = Map.copyOf(loadedStations);
        regions = byRegion.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> new Region(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    public Optional<Station> find(String stationId) {
        if (stationId == null || stationId.length() > 32) {
            return Optional.empty();
        }
        return Optional.ofNullable(stationsById.get(stationId));
    }

    public CatalogResponse response() {
        return new CatalogResponse(SCHEMA_VERSION, SOURCE_NAME, SOURCE_URL,
                sourceDate, regions);
    }

    private double parseLatitude(String value) {
        double latitude = Double.parseDouble(value);
        if (!Double.isFinite(latitude) || latitude < 32 || latitude > 39.5) {
            throw new IllegalStateException("소방서 위도가 대한민국 범위를 벗어났습니다.");
        }
        return latitude;
    }

    private double parseLongitude(String value) {
        double longitude = Double.parseDouble(value);
        if (!Double.isFinite(longitude) || longitude < 124 || longitude > 132) {
            throw new IllegalStateException("소방서 경도가 대한민국 범위를 벗어났습니다.");
        }
        return longitude;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new IllegalStateException("소방서 카탈로그 따옴표가 닫히지 않았습니다.");
        }
        fields.add(field.toString());
        return fields;
    }

    public record CatalogResponse(
            String schemaVersion,
            String sourceName,
            String sourceUrl,
            LocalDate sourceDate,
            List<Region> regions
    ) {
    }

    public record Region(String regionName, List<Station> stations) {
    }

    public record Station(
            String stationId,
            String region,
            String stationName,
            String address,
            double latitude,
            double longitude,
            String phone,
            LocalDate sourceDate
    ) {
        public String stationDisplayName() {
            return region + " " + stationName;
        }
    }
}
