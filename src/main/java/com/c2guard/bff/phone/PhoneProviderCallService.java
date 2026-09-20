package com.c2guard.bff.phone;

import com.c2guard.bff.common.BffContractException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class PhoneProviderCallService {

    private final PhoneIngressProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PhoneProviderCallService(PhoneIngressProperties properties,
                                    JdbcTemplate jdbcTemplate,
                                    TransactionTemplate transactionTemplate,
                                    Clock clock) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public PhoneProviderCallResponse start(PhoneProviderCallStartRequest request,
                                           String ingressToken,
                                           String requestId) {
        requireAuthorized(ingressToken);
        return transactionTemplate.execute(status -> startInTransaction(request, requestId));
    }

    public PhoneProviderCallResponse end(String callId,
                                         PhoneProviderCallEndRequest request,
                                         String ingressToken,
                                         String requestId) {
        requireAuthorized(ingressToken);
        return transactionTemplate.execute(status -> endInTransaction(callId, request, requestId));
    }

    private PhoneProviderCallResponse startInTransaction(PhoneProviderCallStartRequest request,
                                                         String requestId) {
        List<PhoneCallRow> existing = queryByCallIdForUpdate(request.provider(), request.callId());
        if (!existing.isEmpty()) {
            PhoneCallRow row = existing.get(0);
            if (!request.eventId().equals(row.startEventId())) {
                throw new BffContractException(409, "PHONE_CALL_ID_CONFLICT",
                        "같은 provider call ID에 다른 시작 이벤트가 수신되었습니다.", false);
            }
            return response(requestId, row, row.startedAt(), true);
        }

        OffsetDateTime cutoff = OffsetDateTime.ofInstant(
                clock.instant().minus(properties.getClaimMaxAge()), ZoneOffset.UTC);
        List<PhoneCallRow> waiting = jdbcTemplate.query("""
                SELECT incident_id, provider_call_id, provider, start_event_id,
                       end_event_id, session_status, started_at, ended_at, call_end_status
                FROM incident_phone_sessions
                WHERE session_status = 'WAITING_FOR_CALL' AND created_at >= ?
                ORDER BY created_at ASC
                LIMIT 2
                FOR UPDATE
                """, this::map, cutoff);
        if (waiting.isEmpty()) {
            throw new BffContractException(409, "PHONE_SESSION_NOT_WAITING",
                    "연결할 대기 중 전화 세션이 없습니다.", true);
        }
        if (waiting.size() != 1) {
            throw new BffContractException(409, "PHONE_SESSION_AMBIGUOUS",
                    "대기 중 전화 세션이 여러 건이라 통화를 안전하게 연결할 수 없습니다.", true);
        }

        PhoneCallRow candidate = waiting.get(0);
        int updated = jdbcTemplate.update("""
                UPDATE incident_phone_sessions
                SET provider = ?, provider_call_id = ?, start_event_id = ?,
                    session_status = 'IN_CALL', started_at = ?
                WHERE incident_id = ? AND session_status = 'WAITING_FOR_CALL'
                """, request.provider(), request.callId(), request.eventId(),
                request.occurredAt(), candidate.incidentId());
        if (updated != 1) {
            throw new BffContractException(409, "PHONE_SESSION_CLAIM_CONFLICT",
                    "전화 세션이 동시에 변경되었습니다. 다시 시도하세요.", true);
        }
        PhoneCallRow claimed = queryByCallIdForUpdate(request.provider(), request.callId()).get(0);
        return response(requestId, claimed, request.occurredAt(), false);
    }

    private PhoneProviderCallResponse endInTransaction(String callId,
                                                       PhoneProviderCallEndRequest request,
                                                       String requestId) {
        List<PhoneCallRow> rows = queryByCallIdForUpdate(request.provider(), callId);
        if (rows.isEmpty()) {
            throw new BffContractException(404, "PHONE_CALL_NOT_FOUND",
                    "종료할 전화 통화를 찾을 수 없습니다.", false);
        }
        PhoneCallRow row = rows.get(0);
        if (row.endEventId() != null) {
            if (!row.endEventId().equals(request.eventId())
                    || !request.status().equals(row.callEndStatus())) {
                throw new BffContractException(409, "PHONE_CALL_END_CONFLICT",
                        "같은 통화에 다른 종료 이벤트가 수신되었습니다.", false);
            }
            return response(requestId, row, row.endedAt(), true);
        }
        int updated = jdbcTemplate.update("""
                UPDATE incident_phone_sessions
                SET end_event_id = ?, session_status = 'ENDED', ended_at = ?,
                    call_end_status = ?
                WHERE incident_id = ? AND end_event_id IS NULL
                """, request.eventId(), request.occurredAt(), request.status(), row.incidentId());
        if (updated != 1) {
            throw new BffContractException(409, "PHONE_CALL_END_CONFLICT",
                    "전화 통화 종료 상태가 동시에 변경되었습니다.", true);
        }
        PhoneCallRow ended = queryByCallIdForUpdate(request.provider(), callId).get(0);
        return response(requestId, ended, request.occurredAt(), false);
    }

    private List<PhoneCallRow> queryByCallIdForUpdate(String provider, String callId) {
        return jdbcTemplate.query("""
                SELECT incident_id, provider_call_id, provider, start_event_id,
                       end_event_id, session_status, started_at, ended_at, call_end_status
                FROM incident_phone_sessions
                WHERE provider = ? AND provider_call_id = ?
                FOR UPDATE
                """, this::map, provider, callId);
    }

    private PhoneCallRow map(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new PhoneCallRow(
                resultSet.getString("incident_id"),
                resultSet.getString("provider_call_id"),
                resultSet.getString("provider"),
                resultSet.getString("start_event_id"),
                resultSet.getString("end_event_id"),
                resultSet.getString("session_status"),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("ended_at", OffsetDateTime.class),
                resultSet.getString("call_end_status"));
    }

    private PhoneProviderCallResponse response(String requestId, PhoneCallRow row,
                                               OffsetDateTime occurredAt, boolean duplicate) {
        return new PhoneProviderCallResponse(requestId, row.incidentId(), row.callId(),
                row.status(), occurredAt, duplicate);
    }

    private void requireAuthorized(String ingressToken) {
        if (!properties.isEnabled()) {
            throw new BffContractException(404, "PHONE_INGRESS_DISABLED",
                    "전화 provider 수신이 활성화되지 않았습니다.", false);
        }
        if (properties.getToken().isBlank()
                || ingressToken == null
                || !MessageDigest.isEqual(properties.getToken().getBytes(StandardCharsets.UTF_8),
                ingressToken.getBytes(StandardCharsets.UTF_8))) {
            throw new BffContractException(401, "PHONE_INGRESS_UNAUTHORIZED",
                    "전화 provider 인증이 필요합니다.", false);
        }
    }

    private record PhoneCallRow(
            String incidentId,
            String callId,
            String provider,
            String startEventId,
            String endEventId,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            String callEndStatus
    ) {
    }
}
