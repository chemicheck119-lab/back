# 전국 소방서 합성 데모 로그

## 목적과 데이터 경계

소방청 전국소방서 공개 좌표 스냅샷의 215개 소방서 각각에 15개 CAMEO 기반
화학사고 시나리오를 배정해 총 3,225건의 데모 로그를 제공합니다. 이 데이터는 화면,
통계, QA 및 경진대회 시연용이며 실제 신고·출동·대원 확인·운영 기록이 아닙니다.

- 분류: `PUBLIC_SYNTHETIC`
- 운영 기록 여부: `operationalRecord=false`
- 저장소: 전용 테이블 `synthetic_demo_incident_logs`
- 운영 대응기록 테이블과의 혼합: 금지
- 개인정보: 없음
- 생성 방식: 같은 데이터셋 버전에서는 항상 동일하며 재시작 시 중복 적재하지 않음

## 데이터 구성

각 소방서에는 고위험·중간·낮음 등급을 포함한 15개 물질 조합이 저장됩니다.
사고물질, 시설 내 충돌 가능 물질, RuleEngine 근거, 위험, 실제 수행 대응,
브리프 적용 여부, 추가 발견 요인, 최종 대응 결과를 각각 별도 칼럼으로 저장하므로
집계·필터·분석이 가능합니다.

반응성 결과는 NOAA/EPA CAMEO Chemicals 공개 자료에 대한 현재 프로젝트의 검증된
서수 분류를 사용합니다. `LOW`는 안전 보장이 아니며 모든 화면과 API에 합성 데이터
고지문을 유지해야 합니다.

소방서 기준 데이터는 `소방청_전국소방서 좌표현황(XY좌표)` 2024-09-01 스냅샷입니다.
따라서 “전국”은 이 저장소가 고정한 17개 시도·215개 소방서 공개 스냅샷 범위를
뜻하며 실제 기관 시스템과 연계되었다는 의미가 아닙니다.

## 활성화와 적재

기본값은 비활성화입니다.

```text
CHEMICHECK119_DEMO_LOGS_ENABLED=true
CHEMICHECK119_DEMO_LOGS_RECORDS_PER_STATION=15
```

애플리케이션 준비 완료 시 Flyway가 전용 테이블을 만든 뒤 데이터셋 전체를 하나의
트랜잭션으로 적재합니다. 일부만 적재된 동일 버전 데이터가 있으면 시작 과정에서
실패시켜 불완전한 통계를 노출하지 않습니다.

Cloud Run staging 배포에서는 기존 인증형 데모 토글
`GCP_AUTHENTICATED_DEMO_REPLAY_ENABLED=true`에 연결됩니다. 운영 환경에서는 별도로
명시하지 않는 한 적재되지 않습니다.

## API

모든 API는 서명된 소방서 세션이 필요합니다.

```http
GET /api/c2guard/v1/demo/incident-logs?offset=0&limit=20
GET /api/c2guard/v1/demo/incident-logs/coverage
```

첫 번째 API는 세션의 `stationId`에 해당하는 로그만 반환합니다. 두 번째 API는
17개 시도·215개 소방서·3,225건의 적재 상태를 확인하는 QA용 커버리지 요약입니다.
상세 계약은 `contracts/synthetic-demo-logs-v1.openapi.json`에 고정합니다.

## QA 기준

- 17개 시도가 모두 존재해야 함
- 215개 소방서가 모두 존재해야 함
- 모든 소방서가 정확히 15건이어야 함
- 총 로그 수가 3,225건이어야 함
- `data_classification`은 모두 `PUBLIC_SYNTHETIC`이어야 함
- `operational_record`는 모두 `false`여야 함
- 위험도는 `HIGH`, `MEDIUM`, `LOW`를 모두 포함해야 함
- 수행 대응 8종과 최종 대응 결과 6종이 전체 데이터셋에 분포해야 함
