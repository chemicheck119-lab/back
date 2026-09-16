package com.c2guard.bff.record;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.security.BffUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "대응 기록" 목록·상세 조회 BFF.
 * <p>
 * 저장({@link RecordSaveController})과 달리 URL에 사고 ID가 없으므로
 * {@code IncidentPathAuthorizationManager}가 적용되지 않는다. 이 경로는
 * {@code /api/**} 기본 규칙에 따라 인증 여부만 확인하고,
 * 사고 접근 권한은 {@link RecordQueryService#detail}에서 다시 검사한다.
 */
@Validated
@RestController
@RequestMapping("/api/c2guard/v1/records")
public class RecordQueryController {

    private final RecordQueryService service;

    public RecordQueryController(RecordQueryService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public RecordListResponse list(HttpServletRequest httpRequest,
                                   @AuthenticationPrincipal BffUserPrincipal principal) {
        return new RecordListResponse(BffRequestIdFilter.current(httpRequest),
                service.list(principal));
    }

    @GetMapping(value = "/{recordId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public RecordDetailResponse detail(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String recordId,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal BffUserPrincipal principal) {
        return new RecordDetailResponse(BffRequestIdFilter.current(httpRequest),
                service.detail(recordId, principal));
    }
}
