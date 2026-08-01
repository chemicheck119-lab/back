package com.c2guard.auth.staging;

import com.c2guard.security.BffSecurityProperties;
import com.c2guard.security.BffSessionCookieService;
import com.c2guard.security.SignedSessionTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

@RestController
@ConditionalOnProperty(prefix = "chemicheck119.staging-auth", name = "enabled",
        havingValue = "true")
public class StagingAuthController {

    private static final Logger log = LoggerFactory.getLogger(StagingAuthController.class);
    private static final String LOGIN_PATH = "/auth/staging/login";
    private static final String CSRF_COOKIE = "CHEMICHECK119_STAGING_AUTH_CSRF";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StagingAuthProperties properties;
    private final BffSecurityProperties securityProperties;
    private final SignedSessionTokenService tokenService;
    private final BffSessionCookieService sessionCookieService;
    private final StagingLoginAttemptLimiter attemptLimiter;

    public StagingAuthController(StagingAuthProperties properties,
                                 BffSecurityProperties securityProperties,
                                 SignedSessionTokenService tokenService,
                                 BffSessionCookieService sessionCookieService,
                                 StagingLoginAttemptLimiter attemptLimiter) {
        this.properties = properties;
        this.securityProperties = securityProperties;
        this.tokenService = tokenService;
        this.sessionCookieService = sessionCookieService;
        this.attemptLimiter = attemptLimiter;
    }

    @GetMapping(value = LOGIN_PATH, produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> login(HttpServletResponse response) {
        requireReady();
        return page(response, HttpStatus.OK, null);
    }

    @PostMapping(value = LOGIN_PATH,
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> authenticate(
            @RequestParam(defaultValue = "") String userId,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String csrf,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireReady();
        String attemptKey = attemptKey(request);
        if (attemptLimiter.blocked(attemptKey)) {
            log.warn("security_event outcome=staging_login_rate_limited");
            return page(response, HttpStatus.TOO_MANY_REQUESTS,
                    "로그인 시도가 잠시 제한되었습니다. 나중에 다시 시도해주세요.");
        }
        if (!validCsrf(request, csrf)) {
            log.warn("security_event outcome=staging_login_csrf_rejected");
            return page(response, HttpStatus.BAD_REQUEST,
                    "로그인 페이지가 만료되었습니다. 다시 입력해주세요.");
        }
        if (!properties.credentialsMatch(userId, password)) {
            attemptLimiter.failed(attemptKey);
            log.warn("security_event outcome=staging_login_rejected");
            return page(response, HttpStatus.UNAUTHORIZED,
                    "계정 또는 비밀번호가 올바르지 않습니다.");
        }

        attemptLimiter.succeeded(attemptKey);
        String token = tokenService.issue(properties.getUserId(),
                properties.getStationId(), properties.getStationDisplayName(),
                properties.getRoles(), properties.getIncidentScopes());
        sessionCookieService.issue(response, token,
                securityProperties.getSessionMaxAge());
        expireCsrf(response);
        log.info("security_event outcome=staging_login_succeeded");
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(properties.callbackUri())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private ResponseEntity<String> page(HttpServletResponse response,
                                        HttpStatus status, String error) {
        String csrf = newCsrf();
        issueCsrf(response, csrf);
        response.setHeader("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; "
                        + "frame-ancestors 'none'; base-uri 'none'");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        String safeStation = HtmlUtils.htmlEscape(properties.getStationDisplayName());
        String errorHtml = error == null ? "" : "<p class=\"error\">"
                + HtmlUtils.htmlEscape(error) + "</p>";
        String html = """
                <!doctype html>
                <html lang="ko"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>케미체크119 staging 로그인</title>
                <style>body{font-family:system-ui;margin:0;background:#f5f7fa;color:#172033}
                main{max-width:420px;margin:10vh auto;padding:28px;background:white;border-radius:16px}
                label{display:block;margin-top:14px;font-weight:700}input{box-sizing:border-box;width:100%%;
                margin-top:6px;padding:12px;border:1px solid #b8c0cc;border-radius:8px}button{width:100%%;
                margin-top:20px;padding:13px;border:0;border-radius:8px;background:#b42318;color:white;
                font-weight:800}.error{color:#b42318}.notice{font-size:13px;color:#596579}</style></head>
                <body><main><h1>staging 로그인</h1><p>%s</p>%s
                <form method="post" action="%s"><input type="hidden" name="csrf" value="%s">
                <label>계정<input name="userId" autocomplete="username" maxlength="128" required></label>
                <label>비밀번호<input type="password" name="password" autocomplete="current-password" required></label>
                <button type="submit">로그인</button></form>
                <p class="notice">합성 staging 데이터 전용 계정입니다.</p></main></body></html>
                """.formatted(safeStation, errorHtml, LOGIN_PATH, csrf);
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html);
    }

    private void requireReady() {
        if (!properties.isReady()) {
            throw new IllegalStateException("staging 인증 설정이 준비되지 않았습니다.");
        }
    }

    private boolean validCsrf(HttpServletRequest request, String supplied) {
        String stored = request.getCookies() == null ? null
                : Arrays.stream(request.getCookies())
                .filter(cookie -> CSRF_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
        if (stored == null || supplied == null || stored.length() > 128
                || supplied.length() > 128) {
            return false;
        }
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII));
    }

    private String attemptKey(HttpServletRequest request) {
        String source = request.getRemoteAddr();
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", error);
        }
    }

    private String newCsrf() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void issueCsrf(HttpServletResponse response, String value) {
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie(value,
                properties.getCsrfMaxAge()).toString());
    }

    private void expireCsrf(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                csrfCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie csrfCookie(String value, Duration maxAge) {
        return ResponseCookie.from(CSRF_COOKIE, value)
                .httpOnly(true)
                .secure(securityProperties.isCookieSecure())
                .sameSite("Lax")
                .path("/auth/staging")
                .maxAge(maxAge)
                .build();
    }
}
