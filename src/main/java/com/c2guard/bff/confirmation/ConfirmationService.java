package com.c2guard.bff.confirmation;

import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class ConfirmationService {

    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final ConfirmationStore store;
    private final IncidentAccessPolicy incidentAccessPolicy;
    private final Clock clock;

    public ConfirmationService(ConfirmationStore store,
                               IncidentAccessPolicy incidentAccessPolicy,
                               Clock clock) {
        this.store = store;
        this.incidentAccessPolicy = incidentAccessPolicy;
        this.clock = clock;
    }

    public ConfirmationResponse confirm(String incidentId, ConfirmationRequest request,
                                        String requestId, BffUserPrincipal principal) {
        incidentAccessPolicy.requireAccess(principal, incidentId);
        if (request.observedAt().toInstant().isAfter(clock.instant().plus(MAX_FUTURE_SKEW))) {
            throw new IllegalArgumentException("observedAt은 서버 시각보다 5분 이상 미래일 수 없습니다.");
        }
        ConfirmationSaveCommand command = new ConfirmationSaveCommand(
                incidentId, request.role(), request.casNumber(), request.displayName(),
                request.confirmationBasis(), request.observedAt(), principal.userId(),
                principal.organizationId(), requestId);
        return new ConfirmationResponse(requestId, store.save(command).confirmation());
    }

    public ConfirmationCancellationResponse cancel(String incidentId,
                                                   ConfirmationRole role,
                                                   String confirmationId,
                                                   String requestId,
                                                   BffUserPrincipal principal) {
        incidentAccessPolicy.requireAccess(principal, incidentId);
        ConfirmationCancelCommand command = new ConfirmationCancelCommand(
                incidentId, role, confirmationId, principal.userId(),
                principal.organizationId(), requestId);
        return new ConfirmationCancellationResponse(requestId,
                store.cancel(command).cancellation());
    }

    public ConfirmationResponse confirmSyntheticReplay(String incidentId,
                                                       ConfirmationRole role,
                                                       String casNumber,
                                                       String displayName,
                                                       OffsetDateTime observedAt,
                                                       String requestId) {
        ConfirmationSaveCommand command = new ConfirmationSaveCommand(
                incidentId, role, casNumber, displayName,
                ConfirmationBasis.OTHER_VERIFIED_SOURCE, observedAt,
                "synthetic-replay-adapter", "STATION-PUBLIC-DEMO", requestId);
        return new ConfirmationResponse(requestId, store.save(command).confirmation());
    }
}
