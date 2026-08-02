# 구조화 화학사고 대응 데이터

`POST /api/c2guard/v1/incidents/{incidentId}/record`는 원본 대화·AI 분석 JSON과 함께
화재조사·사후분석용 구조화 데이터를 동일 트랜잭션으로 저장한다.

## 데이터 소유권

- 사고물질은 BE가 보관한 `INCIDENT` 현장확인 레코드에서 추출한다.
- 시설 내 충돌 물질·위험등급·위험코드는 저장된 RuleEngine 분석 snapshot에서 추출한다.
- FE는 사고시설, 실제 수행 대응, 브리프 적용 상태, 추가 발견 요인, 최종 결과만 제한된 코드로 제출한다.
- 원본 로그와 모델 응답은 감사·재현을 위해 기존 테이블에 그대로 보존한다.

## 분석 테이블

| 테이블 | 분석 단위 |
| --- | --- |
| `incident_response_summaries` | 사고 1건의 시설·사고물질·브리프 적용·최종 결과 |
| `incident_response_actions` | 실제 수행 대응 1개당 1행 |
| `incident_additional_factors` | 추가 발견 요인 1개당 1행 |
| `incident_conflict_risks` | RuleEngine 충돌 검토 결과 1건 |
| `incident_conflict_hazards` | 위험코드 1개당 1행 |
| `incident_conflict_gas_products` | 예상 가스 생성물 1개당 1행 |

## 보고서용 8개 논리 항목 조회 예시

PostgreSQL에서는 다음과 같이 요청된 8개 논리 항목을 한 행으로 평탄화할 수 있다.

```sql
SELECT
    s.incident_substance_name AS incident_substance,
    s.facility_name AS incident_facility,
    r.facility_substance_name AS conflict_substance,
    concat_ws(' / ', r.risk_level_ko,
        string_agg(DISTINCT h.hazard_code, ', ')) AS risk,
    string_agg(DISTINCT a.action_code, ', ') AS performed_actions,
    s.brief_application_status AS brief_application,
    string_agg(DISTINCT f.factor_code, ', ') AS additional_factors,
    s.final_response_outcome AS final_outcome
FROM incident_response_summaries s
LEFT JOIN incident_conflict_risks r USING (record_id)
LEFT JOIN incident_conflict_hazards h USING (record_id)
LEFT JOIN incident_response_actions a USING (record_id)
LEFT JOIN incident_additional_factors f USING (record_id)
WHERE s.record_id = :record_id
GROUP BY s.record_id, s.incident_substance_name, s.facility_name,
         r.facility_substance_name, r.risk_level_ko,
         s.brief_application_status, s.final_response_outcome;
```

복수 대응·위험·추가요인을 쉼표 문자열 한 컬럼에 원본으로 저장하지 않고 행으로 정규화하므로
소방서·기간·물질·대응별 집계가 가능하다.
