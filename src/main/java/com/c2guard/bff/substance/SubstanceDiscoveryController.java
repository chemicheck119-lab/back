package com.c2guard.bff.substance;

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
@RequestMapping("/api/c2guard/v1/substances")
public class SubstanceDiscoveryController {

    private final SubstanceDiscoveryBffService service;

    public SubstanceDiscoveryController(SubstanceDiscoveryBffService service) {
        this.service = service;
    }

    @PostMapping(value = "/discover", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode discover(@Valid @RequestBody SubstanceDiscoveryRequest request,
                             HttpServletRequest httpRequest) {
        return service.discover(request, BffRequestIdFilter.current(httpRequest));
    }
}
