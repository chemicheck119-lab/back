package com.c2guard.bff.phone;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class PhoneSessionService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final IncidentAccessPolicy access;
    private final Clock clock;
    private final PhoneIngressProperties properties;

    public PhoneSessionService(JdbcTemplate jdbc, TransactionTemplate transactions,
                               IncidentAccessPolicy access, Clock clock, PhoneIngressProperties properties) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.access = access;
        this.clock = clock;
        this.properties = properties;
    }

    public PhoneSessionResponse create(BffUserPrincipal principal, String requestId) {
        access.requireAnalyze(principal, null);
        if (!properties.isEnabled()) throw new BffContractException(503, "PHONE_INGRESS_DISABLED",
                "현재 서버는 전화 접수를 지원하지 않습니다. 운영 설정을 확인해 주세요.", false);
        return transactions.execute(tx -> {
            lockDispatcher();
            var active = jdbc.query("""
                    SELECT * FROM incident_phone_sessions
                    WHERE session_status = 'IN_CALL' OR (session_status = 'WAITING_FOR_CALL'
                      AND (waiting_expires_at > ? OR (waiting_expires_at IS NULL AND created_at >= ?)))
                    ORDER BY created_at ASC FOR UPDATE
                    """, this::map, now(), now().minus(properties.getClaimMaxAge()));
            if (!active.isEmpty()) {
                if (active.size() != 1 || !owns(active.get(0), principal)) {
                    throw new BffContractException(409, "PHONE_DESK_BUSY",
                            "다른 접수 화면이 전화 대기 또는 통화 중입니다. 해당 화면에서 대기를 종료해 주세요.", true);
                }
                return renew(active.get(0), requestId);
            }
            String id = "INC-PHONE-" + UUID.randomUUID();
            var createdAt = now();
            var expiresAt = createdAt.plus(properties.getWaitingLease());
            jdbc.update("INSERT INTO incidents (incident_id, created_at, last_activity_at) VALUES (?, ?, ?)",
                    id, createdAt, createdAt);
            jdbc.update("""
                    INSERT INTO incident_phone_sessions (incident_id, user_id, organization_id,
                      station_display_name, session_id, session_status, created_at, waiting_expires_at)
                    VALUES (?, ?, ?, ?, ?, 'WAITING_FOR_CALL', ?, ?)
                    """, id, principal.userId(), principal.organizationId(), principal.stationDisplayName(),
                    principal.sessionId(), createdAt, expiresAt);
            return response(findOwned(id, principal), requestId);
        });
    }

    public PhoneSessionResponse status(String id, BffUserPrincipal principal, String requestId) {
        access.requireAccess(principal, id);
        return response(findOwned(id, principal), requestId);
    }

    public PhoneSessionResponse heartbeat(String id, BffUserPrincipal principal, String requestId) {
        access.requireAccess(principal, id);
        return transactions.execute(tx -> {
            lockDispatcher();
            var row = findOwned(id, principal);
            // Never resurrect an abandoned lease or change an already claimed call.
            return "WAITING_FOR_CALL".equals(effectiveStatus(row))
                    ? renew(row, requestId) : response(row, requestId);
        });
    }

    public PhoneSessionResponse cancel(String id, BffUserPrincipal principal, String requestId) {
        access.requireAccess(principal, id);
        return transactions.execute(tx -> {
            lockDispatcher();
            var row = findOwned(id, principal);
            if ("IN_CALL".equals(row.status())) {
                throw new BffContractException(409, "PHONE_CALL_IN_PROGRESS",
                        "통화 중에는 대기를 종료할 수 없습니다. 통화 종료 후 다시 시도하세요.", false);
            }
            jdbc.update("""
                    UPDATE incident_phone_sessions SET session_status = 'CANCELED', waiting_expires_at = ?
                    WHERE incident_id = ? AND session_status = 'WAITING_FOR_CALL'
                    """, now(), id);
            return response(findOwned(id, principal), requestId);
        });
    }

    private PhoneSessionResponse renew(Row row, String requestId) {
        if ("WAITING_FOR_CALL".equals(row.status())) {
            jdbc.update("UPDATE incident_phone_sessions SET waiting_expires_at = ? WHERE incident_id = ?",
                    now().plus(properties.getWaitingLease()), row.id());
            row = find(row.id());
        }
        return response(row, requestId);
    }

    private Row findOwned(String id, BffUserPrincipal principal) {
        var row = find(id);
        if (!owns(row, principal)) throw new AccessDeniedException("phone session access denied");
        return row;
    }

    private Row find(String id) {
        List<Row> rows = jdbc.query("SELECT * FROM incident_phone_sessions WHERE incident_id = ?", this::map, id);
        if (rows.isEmpty()) throw new BffContractException(404, "PHONE_SESSION_NOT_FOUND",
                "전화 접수 상태를 찾을 수 없습니다. 새로 접수를 준비해 주세요.", false);
        return rows.get(0);
    }

    private boolean owns(Row row, BffUserPrincipal principal) {
        return principal != null && row.userId().equals(principal.userId())
                && row.organizationId().equals(principal.organizationId())
                && row.sessionId().equals(principal.sessionId());
    }

    private void lockDispatcher() {
        jdbc.queryForObject("SELECT id FROM phone_dispatch_lock WHERE id = 1 FOR UPDATE", Integer.class);
    }

    private OffsetDateTime now() { return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    private String effectiveStatus(Row row) {
        return "WAITING_FOR_CALL".equals(row.status()) && !expiry(row).isAfter(now())
                ? "EXPIRED" : row.status();
    }

    private OffsetDateTime expiry(Row row) {
        return row.expiresAt() == null ? row.createdAt().plus(properties.getClaimMaxAge()) : row.expiresAt();
    }

    private PhoneSessionResponse response(Row row, String requestId) {
        return new PhoneSessionResponse(requestId, row.id(), row.organizationId(), row.stationName(),
                effectiveStatus(row), row.createdAt(), "WAITING_FOR_CALL".equals(row.status()) ? expiry(row) : null);
    }

    private Row map(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        return new Row(rs.getString("incident_id"), rs.getString("user_id"), rs.getString("organization_id"),
                rs.getString("station_display_name"), rs.getString("session_id"), rs.getString("session_status"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("waiting_expires_at", OffsetDateTime.class));
    }

    private record Row(String id, String userId, String organizationId, String stationName,
                       String sessionId, String status, OffsetDateTime createdAt, OffsetDateTime expiresAt) {}
}
