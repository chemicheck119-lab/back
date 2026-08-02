package com.c2guard.security;

import com.c2guard.station.FireStationCatalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SessionContextResponse(
        String schemaVersion,
        String requestId,
        String userId,
        String stationId,
        String stationDisplayName,
        StationLocation stationLocation,
        List<String> roles,
        List<String> incidentScopes,
        Instant issuedAt,
        Instant expiresAt
) {
    public SessionContextResponse(String requestId, BffUserPrincipal principal,
                                  FireStationCatalog.Station station) {
        this("chemicheck119-dashboard-bff-v1", requestId, principal.userId(),
                principal.organizationId(), principal.stationDisplayName(),
                station == null ? null : new StationLocation(station.address(),
                        station.latitude(), station.longitude(), station.phone(),
                        "NFA_PUBLIC_DATA", FireStationCatalog.SOURCE_NAME,
                        FireStationCatalog.SOURCE_URL, station.sourceDate()),
                principal.roles().stream().map(Enum::name).sorted().toList(),
                principal.incidentScopes().stream().sorted().toList(),
                principal.issuedAt(), principal.expiresAt());
    }

    public record StationLocation(
            String address,
            double latitude,
            double longitude,
            String phone,
            String coordinateSource,
            String sourceName,
            String sourceUrl,
            LocalDate sourceDate
    ) {
    }
}
