package com.c2guard.bff.phone;

import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class PhoneSessionService {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final IncidentAccessPolicy incidentAccessPolicy;
    private final Clock clock;

    public PhoneSessionService(JdbcTemplate jdbcTemplate,
                               TransactionTemplate transactionTemplate,
                               IncidentAccessPolicy incidentAccessPolicy,
                               Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.incidentAccessPolicy = incidentAccessPolicy;
        this.clock = clock;
    }

    public PhoneSessionResponse create(BffUserPrincipal principal, String requestId) {
        incidentAccessPolicy.requireAnalyze(principal, null);
        String incidentId = "INC-PHONE-" + UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO incidents (incident_id, created_at, last_activity_at)
                    VALUES (?, ?, ?)
                    """, incidentId, createdAt, createdAt);
            jdbcTemplate.update("""
                    INSERT INTO incident_phone_sessions (
                        incident_id, user_id, organization_id, station_display_name,
                        session_id, session_status, created_at
                    ) VALUES (?, ?, ?, ?, ?, 'WAITING_FOR_CALL', ?)
                    """, incidentId, principal.userId(), principal.organizationId(),
                    principal.stationDisplayName(), principal.sessionId(), createdAt);
        });
        return new PhoneSessionResponse(requestId, incidentId, principal.organizationId(),
                principal.stationDisplayName(), "WAITING_FOR_CALL", createdAt);
    }
}
