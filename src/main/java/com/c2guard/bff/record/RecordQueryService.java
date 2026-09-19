package com.c2guard.bff.record;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * "대응 기록" 목록·상세 조회.
 * <p>
 * 목록은 저장 시점의 소속(organizationId) 단위로 범위를 제한한다. 상세는 목록보다
 * 넓게 새어나가지 않도록, 조회 시점에 다시 한 번 사고 접근 권한
 * ({@link IncidentAccessPolicy})을 확인한 뒤에만 대화·구조화 결과를 반환한다.
 * 저장 당시 같은 소속이었다는 사실만으로 상세 열람까지 허용하지 않는다.
 */
@Service
public class RecordQueryService {

    private static final int LIST_LIMIT = 200;

    private final ResponseRecordStore recordStore;
    private final IncidentAccessPolicy incidentAccessPolicy;

    public RecordQueryService(ResponseRecordStore recordStore,
                              IncidentAccessPolicy incidentAccessPolicy) {
        this.recordStore = recordStore;
        this.incidentAccessPolicy = incidentAccessPolicy;
    }

    public List<RecordSummary> list(BffUserPrincipal principal) {
        return recordStore.listSummariesForOrganization(
                principal.organizationId(), LIST_LIMIT);
    }

    public RecordDetail detail(String recordId, BffUserPrincipal principal) {
        RecordDetail detail = recordStore.findDetail(recordId)
                .orElseThrow(() -> new BffContractException(404, "RECORD_NOT_FOUND",
                        "요청한 대응 기록을 찾을 수 없습니다.", false));
        incidentAccessPolicy.requireAccess(principal, detail.incidentId());
        return detail;
    }
}
