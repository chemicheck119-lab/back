package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.PublicAnalysisPrincipalProvider;
import com.fasterxml.jackson.databind.JsonNode;
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
@RequestMapping("/api/c2guard/v1/incidents")
public class IncidentBriefController {

    private final IncidentBriefBffService service;
    private final PublicAnalysisPrincipalProvider principalProvider;

    public IncidentBriefController(IncidentBriefBffService service,
                                   PublicAnalysisPrincipalProvider principalProvider) {
        this.service = service;
        this.principalProvider = principalProvider;
    }

    @PostMapping(value = "/{incidentId}/brief",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode brief(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String incidentId,
            @Valid @RequestBody IncidentBriefRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal BffUserPrincipal principal) {
        return service.brief(request.toCommand(incidentId),
                BffRequestIdFilter.current(httpRequest),
                principalProvider.resolve(principal));
    }
}