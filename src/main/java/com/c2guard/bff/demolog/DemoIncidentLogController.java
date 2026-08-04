package com.c2guard.bff.demolog;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.security.BffUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/c2guard/v1/demo/incident-logs")
public class DemoIncidentLogController {

    private final DemoIncidentLogService service;

    public DemoIncidentLogController(DemoIncidentLogService service) {
        this.service = service;
    }

    @GetMapping
    DemoIncidentLogPageResponse findForAuthenticatedStation(
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            HttpServletRequest request,
            @AuthenticationPrincipal BffUserPrincipal principal) {
        return service.findForStation(principal,
                BffRequestIdFilter.current(request), offset, limit);
    }

    @GetMapping("/coverage")
    DemoIncidentLogCoverageResponse coverage(HttpServletRequest request) {
        return service.coverage(BffRequestIdFilter.current(request));
    }
}
