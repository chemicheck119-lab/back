# 케미체크119 BFF v1 계약 및 레거시 전환 정책

## 1. 결정 상태

- 구현·통합 기준 브랜치: `develop`
- 기능 브랜치: `develop`에서 분기하고 PR base를 `develop`으로 설정
- BFF schema: `chemicheck119-dashboard-bff-v1`
- Model API schema: `chemiguard119-api-v1`
- upstream 기준: `chemicheck119/llm` PR #31 merge commit
  `e24fa93d538229844af4377976ee5a180c881fb3`
- connect timeout: 2초
- 전체 model response timeout: 15초
- timeout 응답: HTTP 504, `MODEL_TIMEOUT`, `retryable=true`

FE는 BE/BFF만 호출합니다. AI API Key와 지도 사업자 Secret은 브라우저·응답·로그에
노출하지 않습니다.

## 2. 공개 BFF 경로

| 목적 | FE → BE | BE의 하위 의존성 | 성공 |
|---|---|---|---|
| 세션확인 | `GET /api/c2guard/v1/session` | 서명 session cookie | 200 |
| 로그아웃 | `POST /api/c2guard/v1/logout` | session cookie 만료 | 204 |
| 사고분석 | `POST /api/c2guard/v1/incidents/analyze` | Model API `/api/v1/incidents/analyze` | 200 |
| 물질발견 | `POST /api/c2guard/v1/substances/discover` | Model API `/api/v1/substances/discover` | 200 |
| 현장확인 | `POST /api/c2guard/v1/incidents/{incidentId}/confirmations` | BE 확인 저장소 | 201 |
| 이동갱신 | `POST /api/c2guard/v1/incidents/{incidentId}/movement` | BE 위치·길찾기 provider | 200 |
| 기록저장 | `POST /api/c2guard/v1/incidents/{incidentId}/record` | BE 영구 저장소 | 201 |

기계 판독 계약은 `contracts/dashboard-bff-v1.openapi.json`입니다. 요청·응답 예시는
`contracts/examples/bff`에서 관리합니다.

## 3. 인증 경계

OpenAPI의 `ServiceSession`은 `CHEMICHECK119_SESSION` cookie를 사용합니다. BE는 신뢰된
인증 adapter가 발급한 HS256 session의 사용자·소속·역할·incident scope와 만료를
검증합니다. 세부 운영 계약은 `docs/BFF_SECURITY.md`를 기준으로 합니다.

- 401: 인증 자격증명이 없거나 유효하지 않음
- 403: 인증은 됐지만 해당 incident 또는 기능에 권한이 없음
- 모든 BFF 경로에서 FE 사용자 인증과 incident 접근권한을 검사
- BE → AI 호출에만 `X-API-Key` 사용
- 인입·하위 호출·응답·구조화 로그가 같은 request ID 사용

CORS는 인증이 아닙니다. 운영 origin allowlist는 환경별 설정으로 관리하며 개발 origin은
운영 profile에 포함하지 않습니다.

## 4. 공통 오류 계약

모든 오류는 다음 형태를 유지합니다.

```json
{
  "schemaVersion": "chemicheck119-dashboard-bff-v1",
  "requestId": "REQ-...",
  "error": {
    "code": "MODEL_TIMEOUT",
    "message": "사용자에게 표시할 안전한 메시지",
    "retryable": true
  },
  "resetAllowed": false
}
```

| HTTP | 대표 코드 | retryable | 의미 |
|---|---|---:|---|
| 400 | `INVALID_REQUEST` | false | FE 요청 형식·도메인 입력 오류 |
| 401 | `AUTH_REQUIRED` | false | FE 사용자 인증 필요 |
| 403 | `ACCESS_DENIED` | false | incident 접근권한 없음 |
| 409 | `INCIDENT_REFERENCE_CONFLICT` | false | 다른 사고 또는 존재하지 않는 권위 ID |
| 422 | `MODEL_CONTRACT_VIOLATION` | false | BE→AI 요청/응답 계약 drift |
| 500 | `INTERNAL_ERROR` | false | BE 내부 또는 출력 안전검증 실패 |
| 503 | `MODEL_SERVICE_UNAVAILABLE` | true | AI readiness·artifact·일시 장애 |
| 504 | `MODEL_TIMEOUT` | true | AI-backed 요청이 전체 15초 timeout 초과 |

`MODEL_TIMEOUT`은 `incidents/analyze`와 `substances/discover`에 적용합니다. confirmation,
movement, record는 각각 저장소·route provider의 명시적 오류 정책을 사용하며 AI timeout을
가장하지 않습니다. 재시도 여부는 `retryable`이 결정하고 FE는 재시도 중에도 현재 화면을
유지합니다.

## 5. 안전 상태와 하위 호환

- `AWAITING_*_CONFIRMATION`에서는 위험등급·구체적 반응·대응 권고를 만들지 않습니다.
- 확인된 INCIDENT/FACILITY CAS 두 개가 있어야 충돌 규칙을 실행합니다.
- 시설 이력은 `HISTORICAL_CANDIDATE_NOT_CURRENT_INVENTORY` 의미를 보존합니다.
- 후보 점수, LOW 등급, 이동 progress를 확률이나 “안전”으로 바꾸지 않습니다.
- 근거·경고·request ID·model/data/rule version을 축약하지 않습니다.
- 저장 성공의 `resetAllowed=true`를 받기 전에는 FE 초기화를 허용하지 않습니다.

## 6. 레거시 경로 전환

일정이 확정되지 않았으므로 임의의 날짜형 `Sunset` header는 만들지 않습니다. v1 스테이징
E2E가 승인되고 FE가 v1으로 전환된 다음 한 번의 안정화 릴리스까지 아래 정책을 유지합니다.

| 레거시 경로 | 정책 |
|---|---|
| `POST /api/incident-check` | deprecated. 신규 FE 사용 금지. v1 안전 상태로 축소 변환할 수 없으므로 별도 legacy 응답 유지 |
| `POST /api/compatibility/check` | deprecated. 현장 confirmation gate를 우회하므로 v1 내부에서 호출 금지 |
| `GET /api/facilities/{name}/substances` | deprecated. 결과는 과거 이력 후보이며 현재 재고가 아님을 명시 |
| `GET /api/facilities/search` | deprecated. v1 사고분석/물질발견의 권위 결과로 사용 금지 |
| `POST /api/c2guard/records` | deprecated. v1 record의 권위 ID·대화·snapshot을 표현하지 못하므로 adapter 저장 금지 |
| `GET /api/c2guard/records` | 영구 저장 전환 기간의 legacy read projection으로만 유지 |
| `/api/c2guard/input/parse` | 실제 구현이 없으므로 새로 만들지 않음 |
| `/api/c2guard/rule-engine/check-pair` | 실제 구현이 없으며 confirmation gate를 우회하므로 새로 만들지 않음 |

구현 이슈에서 레거시 응답에는 `Deprecation: true`와 이 문서 또는 v1 OpenAPI를 가리키는
`Link` header를 추가합니다. 실제 제거는 #11 스테이징 승인과 별도 release decision 후에만
진행합니다.

현재 구현된 레거시 경로는 공통 filter에서 `Deprecation: true`와 이 문서의 `develop` URL을
반환합니다. `Sunset`은 실제 제거 릴리스가 승인되기 전까지 설정하지 않습니다.

## 7. 계약 갱신과 검증

1. AI `main`의 새 commit과 PR을 확인합니다.
2. `contracts/upstream/model-api-v1.openapi.json`과 fixture를 갱신합니다.
3. BFF 로컬 결정과 충돌 여부를 검토합니다.
4. `contracts/contract-lock.json`의 commit과 SHA-256을 갱신합니다.
5. 다음 검증을 실행합니다.

```bash
bash gradlew test --tests com.c2guard.contract.BffContractSnapshotTest
```

계약 drift는 명시적 검토와 lock 갱신 없이 통과시키지 않습니다.
