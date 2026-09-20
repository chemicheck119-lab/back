package com.c2guard.bff.phone;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.security.BffUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/c2guard/v1/phone-sessions")
public class PhoneSessionController {

    private final PhoneSessionService service;

    public PhoneSessionController(PhoneSessionService service) {
        this.service = service;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PhoneSessionResponse create(@AuthenticationPrincipal BffUserPrincipal principal,
                                       HttpServletRequest request) {
        return service.create(principal, BffRequestIdFilter.current(request));
    }
}
