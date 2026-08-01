package com.c2guard.bff.movement;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.security.BffUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/c2guard/v1/incidents/{incidentId}/movement")
public class MovementController {

    private final MovementService service;

    public MovementController(MovementService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public MovementUpdateResponse update(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String incidentId,
            @Valid @RequestBody MovementUpdateRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal BffUserPrincipal principal) {
        return service.update(incidentId, request,
                BffRequestIdFilter.current(httpRequest), principal);
    }
}
