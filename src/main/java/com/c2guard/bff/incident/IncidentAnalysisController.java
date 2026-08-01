package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/c2guard/v1/incidents")
public class IncidentAnalysisController {

    private final IncidentAnalysisBffService service;

    public IncidentAnalysisController(IncidentAnalysisBffService service) {
        this.service = service;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode analyze(@Valid @RequestBody IncidentAnalyzeRequest request,
                            HttpServletRequest httpRequest) {
        return service.analyze(request, BffRequestIdFilter.current(httpRequest));
    }
}
