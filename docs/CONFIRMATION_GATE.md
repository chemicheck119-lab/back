# 현장 물질 confirmation과 재분석 gate

## API

```text
POST /api/c2guard/v1/incidents/{incidentId}/confirmations
```

요청은 `role`, `casNumber`, `displayName`, `confirmationBasis`, `observedAt`을 받습니다.
BE는 session principal의 사용자·소속, 서버 `createdAt`, 인입 request ID와 server-generated
`confirmationId`를 저장하고 HTTP 201과 `reanalyzeRequired=true`를 반환합니다. FE가
confirmation ID, 확인 사용자 또는 생성 시각을 정할 수 없습니다.

CAS는 `2~7자리-2자리-1자리` 형식과 CAS Registry Number check digit를 모두 검증합니다.
`observedAt`은 원래 offset을 보존하며 서버 시각보다 5분 이상 미래면 400입니다.

## 중복·정정·취소 정책

- 같은 incident·role에서 CAS, 표시명, 근거, 관측 시각이 같은 exact retry는 기존
  confirmation ID와 최초 감사정보를 반환합니다.
- 같은 incident·role의 내용이 달라지면 새 ID와 revision을 추가하고 직전 활성 레코드를
  `SUPERSEDED`로 표시합니다. 과거 레코드는 삭제하거나 덮어쓰지 않습니다.
- 저장은 incident·role 단위 원자 연산으로 직렬화되어 동시 exact retry도 권위 레코드 하나만
  생성합니다.
- v1 공개 계약에는 취소 endpoint가 없으므로 삭제·취소를 허용하지 않습니다. 잘못된 확인은
  새 correction으로 정정하며, 별도 취소가 필요하면 OpenAPI와 감사정책을 먼저 추가합니다.

운영 경로는 PostgreSQL과 Flyway migration으로 confirmation head·revision·과거 이력을
영속화합니다. 로컬 단위 테스트의 process-local 대체 저장소와 운영 DB 경로는 같은 멱등·정정
계약을 따르지만, 재시작 복구 근거는 DB 통합 테스트에서 별도로 검증합니다.

## AI 재분석 연결

FE는 confirmation 성공 직후 동일 `incidentId`로 사고분석 BFF를 다시 호출합니다. BE는
FE가 보낸 후보나 confirmation ID를 신뢰하지 않고 저장소에서 해당 incident의 활성 레코드만
조회합니다.

- INCIDENT만 활성: `confirmed_incident_substance`만 전달, 시설 확인 gate 유지
- FACILITY만 활성: `confirmed_facility_substance`만 전달, 사고물질 확인 gate 유지
- 둘 다 활성: 서로 다른 권위 ID를 가진 두 객체를 전달해 AI Rule 실행 조건 충족

BE는 각 analysis snapshot에 실제 요청에 사용한 INCIDENT·FACILITY confirmation ID를 함께
저장합니다. 분석 도중 활성 confirmation이 바뀌거나, 새 confirmation 뒤 Agent가 새 분석 없이
과거 snapshot을 반환하면 결과를 표시하지 않고 재분석을 요구합니다. 최종 대응 기록도 현재
활성 confirmation 전체와 결합된 최신 analysis만 허용하므로 정정 전 충돌 결과를 새 확인과
섞어 저장할 수 없습니다. 과거 snapshot과 `SUPERSEDED` confirmation은 감사 이력으로 보존됩니다.

AI 객체는 `confirmation_id`, `cas_number`, `display_name`, `role`,
`presence_status=CONFIRMED_PRESENT`, `confirmation_basis`, `observed_at`만 포함합니다. 후보 점수,
과거 시설 이력 또는 FE 선택값은 확인 객체로 승격하지 않습니다.

## 검증

```bash
bash gradlew clean test --no-daemon --console=plain
```

CAS 형식/check digit, 인증 사용자·서버 시각·request ID, 201 응답, exact retry, correction
revision, 동시 중복, incident scope 403, 한쪽/양쪽 AI 요청 projection, snapshot–confirmation
결합과 stale 결과 차단을 검증합니다.
