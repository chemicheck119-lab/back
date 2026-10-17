package com.c2guard.security;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.bff.common.DashboardErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(CostAdmissionProperties.class)
public class CostAdmissionConfiguration implements WebMvcConfigurer {
    private static final String LEASE = CostAdmissionConfiguration.class.getName() + ".lease";
    private final CostAdmissionGuard guard;
    private final CostAdmissionProperties properties;
    private final BffSecurityProperties security;
    private final ObjectMapper mapper;

    public CostAdmissionConfiguration(CostAdmissionGuard guard, CostAdmissionProperties properties,
                                      BffSecurityProperties security, ObjectMapper mapper) {
        this.guard = guard; this.properties = properties; this.security = security; this.mapper = mapper;
    }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                               Object handler) throws Exception {
                if (!properties.isEnabled() || !"POST".equals(request.getMethod())) return true;
                var auth = SecurityContextHolder.getContext().getAuthentication();
                String identity;
                if (auth != null && auth.getPrincipal() instanceof BffUserPrincipal user) {
                    identity = user.organizationId().length() + ":" + user.organizationId() + user.userId();
                } else if (security.isPublicAnalysisEnabled() && isPublicAnalysis(request.getRequestURI())) {
                    // Shared anonymous quota; never trust caller-supplied IP/user headers.
                    identity = "PUBLIC_SHARED";
                } else return true; // Existing security owns 401/403 and provider-token checks.
                var lease = guard.tryAcquire(identity);
                if (lease != null) { request.setAttribute(LEASE, lease); return true; }
                response.setStatus(429);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Retry-After", "60");
                response.setHeader("Cache-Control", "no-store");
                mapper.writeValue(response.getOutputStream(), new DashboardErrorResponse(
                    BffRequestIdFilter.current(request), "CAPACITY_LIMITED",
                    "처리 한도에 도달했습니다. 잠시 후 직접 다시 요청하세요.", false));
                return false;
            }
            @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                                   Object handler, Exception error) {
                if (request.getAttribute(LEASE) instanceof CostAdmissionGuard.Lease lease) lease.close();
            }
        }).addPathPatterns("/api/c2guard/v1/incidents/analyze", "/api/c2guard/v1/incidents/brief",
                "/api/c2guard/v1/substances/discover", "/api/c2guard/v1/transcriptions",
                "/api/c2guard/v1/incidents/*/transcriptions");
    }

    private static boolean isPublicAnalysis(String path) {
        return path.equals("/api/c2guard/v1/incidents/analyze")
            || path.equals("/api/c2guard/v1/incidents/brief")
            || path.equals("/api/c2guard/v1/substances/discover");
    }
}
