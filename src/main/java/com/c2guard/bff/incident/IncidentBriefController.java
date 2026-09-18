package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.PublicAnalysisPrincipalProvider;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신규 행동 카드({@code action-brief-v1}) 모델 API 연동 BFF.
 * <p>
 * v1: 모델 API 응답을 가공 없이 그대로 반환한다. 화면용 title/message 우선순위 재배치 등은
 * FE 피드백을 받은 뒤 이 컨트롤러의 응답을 감싸는 형태로 반복 개선한다.
 */
@RestController
@RequestMapping("/api/c2guard/v1/incidents")
public class IncidentBriefController {

    private final IncidentBriefBffService service;
    private final PublicAnalysisPrincipalProvider principalProvider;

    public IncidentBriefController(IncidentBriefBffService service,
                                   PublicAnalysisPrincipalProvider principalProvider) {
        this.service = service;
        this.principalProvider = principalProvider;
    }

    @PostMapping(value = "/brief", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode brief(@Valid @RequestBody IncidentBriefRequest request,
                          HttpServletRequest httpRequest,
                          @AuthenticationPrincipal BffUserPrincipal principal) {
        IncidentBriefCommand command = new IncidentBriefCommand(request.analysis(),
                request.invalidatedConfirmationIds(), request.reportedEvidenceConflict());
        return service.brief(command, BffRequestIdFilter.current(httpRequest),
                principalProvider.resolve(principal));
    }
}
