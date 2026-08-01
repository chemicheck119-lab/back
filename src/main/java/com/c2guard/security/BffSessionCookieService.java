package com.c2guard.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;

@Component
public class BffSessionCookieService {

    private final BffSecurityProperties properties;

    public BffSessionCookieService(BffSecurityProperties properties) {
        this.properties = properties;
    }

    public void issue(HttpServletResponse response, String token, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, maxAge).toString());
    }

    public void expire(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.getCookieName(), value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getCookieSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
