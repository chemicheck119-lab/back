package com.c2guard.bff.intake;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.bff.confirmation.ConfirmationRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/c2guard/v1/intake/replays/{incidentId}/confirmations")
public class SyntheticReplayConfirmationController {

    private final SyntheticReplayConfirmationService service;

    public SyntheticReplayConfirmationController(SyntheticReplayConfirmationService service) {
        this.service = service;
    }

    @PostMapping(value = "/{role}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SyntheticReplayConfirmationResponse confirm(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String incidentId,
            @PathVariable ConfirmationRole role,
            HttpServletRequest request) {
        return service.confirm(incidentId, role, BffRequestIdFilter.current(request));
    }
}
