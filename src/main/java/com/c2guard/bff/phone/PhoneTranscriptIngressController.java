package com.c2guard.bff.phone;

import com.c2guard.bff.common.BffRequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/c2guard/v1/incidents/{incidentId}/phone-transcripts")
public class PhoneTranscriptIngressController {

    private final PhoneTranscriptIngressService service;

    public PhoneTranscriptIngressController(PhoneTranscriptIngressService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PhoneTranscriptIngressResponse accept(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String incidentId,
            @Valid @RequestBody PhoneTranscriptIngressRequest request,
            @RequestHeader(value = "X-Phone-Ingress-Token", required = false) String ingressToken,
            HttpServletRequest httpRequest) {
        return service.accept(incidentId, request, ingressToken,
                BffRequestIdFilter.current(httpRequest));
    }
}
