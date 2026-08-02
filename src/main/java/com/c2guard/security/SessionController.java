package com.c2guard.security;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.station.FireStationCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/c2guard/v1")
public class SessionController {

    private final BffSessionCookieService cookieService;
    private final FireStationCatalog stationCatalog;

    public SessionController(BffSessionCookieService cookieService,
                             FireStationCatalog stationCatalog) {
        this.cookieService = cookieService;
        this.stationCatalog = stationCatalog;
    }

    @GetMapping("/session")
    public SessionContextResponse session(
            HttpServletRequest request,
            @AuthenticationPrincipal BffUserPrincipal principal) {
        return new SessionContextResponse(BffRequestIdFilter.current(request), principal,
                stationCatalog.find(principal.organizationId()).orElse(null));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse response) {
        cookieService.expire(response);
    }
}
