package com.c2guard.security;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.bff.common.DashboardErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class BffSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public BffSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_REQUIRED",
                "사용자 인증이 필요합니다.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        write(request, response, HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED",
                "이 사고 또는 기능에 접근할 권한이 없습니다.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       int status, String code, String message) throws IOException {
        String requestId = BffRequestIdFilter.current(request);
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(BffRequestIdFilter.HEADER, requestId);
        objectMapper.writeValue(response.getOutputStream(),
                new DashboardErrorResponse(requestId, code, message, false));
    }
}
