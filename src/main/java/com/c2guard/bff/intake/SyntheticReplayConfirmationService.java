package com.c2guard.bff.intake;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.bff.confirmation.ConfirmationResponse;
import com.c2guard.bff.confirmation.ConfirmationRole;
import com.c2guard.bff.confirmation.ConfirmationService;
import com.c2guard.bff.confirmation.ConfirmationStore;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class SyntheticReplayConfirmationService {

    private final IncidentReplayProperties properties;
    private final SyntheticIncidentReplayRegistry registry;
    private final ConfirmationService confirmationService;
    private final ConfirmationStore confirmationStore;

    public SyntheticReplayConfirmationService(IncidentReplayProperties properties,
                                              SyntheticIncidentReplayRegistry registry,
                                              ConfirmationService confirmationService,
                                              ConfirmationStore confirmationStore) {
        this.properties = properties;
        this.registry = registry;
        this.confirmationService = confirmationService;
        this.confirmationStore = confirmationStore;
    }

    public SyntheticReplayConfirmationResponse confirm(String incidentId,
                                                       ConfirmationRole role,
                                                       String requestId) {
        if (!properties.isEnabled() || !properties.isPublicEndpointEnabled()
                || !properties.isSyntheticConfirmationEnabled()) {
            throw new BffContractException(404, "SYNTHETIC_CONFIRMATION_DISABLED",
                    "공개 합성 현장 확인이 비활성화되어 있습니다.", false);
        }
        IncidentEnvelope envelope = registry.requireActive(incidentId);
        SyntheticReplaySubstance substance = SyntheticReplaySubstance.forRole(role);
        OffsetDateTime observedAt = OffsetDateTime.ofInstant(envelope.receivedAt(),
                ZoneOffset.UTC);
        ConfirmationResponse saved = confirmationService.confirmSyntheticReplay(
                incidentId, substance.role(), substance.casNumber(), substance.displayName(),
                observedAt, requestId);
        int confirmedCount = confirmationStore.findActiveForIncident(incidentId).size();
        return new SyntheticReplayConfirmationResponse(
                SyntheticReplayConfirmationResponse.SCHEMA_VERSION,
                requestId,
                incidentId,
                saved.confirmationId(),
                substance.role(),
                substance.casNumber(),
                substance.displayName(),
                IncidentDataClassification.PUBLIC_SYNTHETIC,
                SyntheticReplayConfirmationResponse.CONFIRMATION_TYPE,
                saved.createdAt(),
                confirmedCount,
                confirmedCount == ConfirmationRole.values().length,
                true,
                SyntheticReplayConfirmationResponse.DISCLOSURE);
    }
}
