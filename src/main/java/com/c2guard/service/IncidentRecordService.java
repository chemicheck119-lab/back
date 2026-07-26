package com.c2guard.service;

import com.c2guard.domain.IncidentRecord;
import com.c2guard.dto.IncidentRecordRequest;
import com.c2guard.dto.IncidentRecordResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 대응기록 저장/조회.
 * <p>
 * SubstanceRegistryService와 마찬가지로 지금은 DB 없이 메모리(List)에만 쌓는다.
 * TODO: JPA/H2 연동되면 IncidentRecordRepository로 교체.
 */
@Service
public class IncidentRecordService {

    private final List<IncidentRecord> records = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public IncidentRecordResponse save(IncidentRecordRequest request) {
        IncidentRecord record = new IncidentRecord(
                idGenerator.getAndIncrement(),
                request.getFacilityName(),
                request.getIncidentSubstance(),
                request.getHeldSubstanceCount(),
                request.isHasIncompatible(),
                request.getStation(),
                LocalDateTime.now()
        );
        records.add(record);
        return toResponse(record);
    }

    /**
     * 최신 확인 건이 먼저 나오도록 역순으로 반환. ("대응기록 조회" 화면용)
     */
    public List<IncidentRecordResponse> findAll() {
        List<IncidentRecord> reversed = new ArrayList<>(records);
        Collections.reverse(reversed);
        return reversed.stream().map(this::toResponse).toList();
    }

    private IncidentRecordResponse toResponse(IncidentRecord r) {
        return new IncidentRecordResponse(
                r.getId(), r.getFacilityName(), r.getIncidentSubstance(),
                r.getHeldSubstanceCount(), r.isHasIncompatible(), r.getStation(), r.getConfirmedAt()
        );
    }
}