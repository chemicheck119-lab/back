package com.c2guard.security;

import com.c2guard.bff.common.BffRequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Component
public class BffSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BffSessionAuthenticationFilter.class);

    private final SignedSessionTokenService tokenService;
    private final BffSecurityProperties properties;
    private final BffSessionCookieService cookieService;
    private final Clock clock;

    public BffSessionAuthenticationFilter(SignedSessionTokenService tokenService,
                                          BffSecurityProperties properties,
                                          BffSessionCookieService cookieService,
                                          Clock clock) {
        this.tokenService = tokenService;
        this.properties = properties;
        this.cookieService = cookieService;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = findSessionCookie(request);
        if (token != null) {
            try {
                BffUserPrincipal principal = tokenService.verify(token);
                List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                principal, null, authorities));
                refreshCookie(response, token, principal);
            } catch (SessionTokenException error) {
                expireCookie(response);
                log.warn("security_event requestId={} outcome=invalid_session",
                        BffRequestIdFilter.current(request));
            }
        }
        filterChain.doFilter(request, response);
    }

    private String findSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.getCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void refreshCookie(HttpServletResponse response, String token,
                               BffUserPrincipal principal) {
        Duration remaining = Duration.between(clock.instant(), principal.expiresAt());
        if (remaining.isNegative()) {
            remaining = Duration.ZERO;
        }
        cookieService.issue(response, token, remaining);
    }

    private void expireCookie(HttpServletResponse response) {
        cookieService.expire(response);
    }
}
