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
@RequestMapping("/api/c2guard/v1/phone-provider/calls")
public class PhoneProviderCallController {

    private final PhoneProviderCallService service;

    public PhoneProviderCallController(PhoneProviderCallService service) {
        this.service = service;
    }

    @PostMapping(value = "/start", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PhoneProviderCallResponse start(
            @Valid @RequestBody PhoneProviderCallStartRequest body,
            @RequestHeader(value = "X-Phone-Ingress-Token", required = false) String ingressToken,
            HttpServletRequest request) {
        return service.start(body, ingressToken, BffRequestIdFilter.current(request));
    }

    @PostMapping(value = "/{callId}/end", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PhoneProviderCallResponse end(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,160}$") String callId,
            @Valid @RequestBody PhoneProviderCallEndRequest body,
            @RequestHeader(value = "X-Phone-Ingress-Token", required = false) String ingressToken,
            HttpServletRequest request) {
        return service.end(callId, body, ingressToken, BffRequestIdFilter.current(request));
    }
}
