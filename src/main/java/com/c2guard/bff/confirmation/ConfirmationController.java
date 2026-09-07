package com.c2guard.bff.confirmation;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.security.BffUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/c2guard/v1/incidents/{incidentId}/confirmations")
public class ConfirmationController {

    private final ConfirmationService service;

    public ConfirmationController(ConfirmationService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ConfirmationResponse confirm(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String incidentId,
            @Valid @RequestBody ConfirmationRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal BffUserPrincipal principal) {
        return service.confirm(incidentId, request,
                BffRequestIdFilter.current(httpRequest), principal);
    }

    @DeleteMapping(value = "/{role}/{confirmationId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ConfirmationCancellationResponse cancel(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String incidentId,
            @PathVariable ConfirmationRole role,
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String confirmationId,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal BffUserPrincipal principal) {
        return service.cancel(incidentId, role, confirmationId,
                BffRequestIdFilter.current(httpRequest), principal);
    }
}
