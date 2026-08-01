# 사고분석 BFF 연동

FE는 `POST /api/c2guard/v1/incidents/analyze`만 호출하고 AI `/api/v1/**` 경로나
`X-API-Key`를 사용하지 않는다. BE는 다음 순서로 요청을 처리한다.

1. FE의 `X-Request-Id`를 검증하거나 새 ID를 생성한다.
2. BFF camelCase 요청을 검증하고 Model API snake_case 요청으로 변환한다.
3. 같은 incident의 활성 confirmation을 서버 저장소에서 조회해 확인 객체로 추가한다.
4. 사고별 최신 agent memory를 불러와 기존 분석 요청을 `analysis`로 감싼다.
5. 같은 request ID와 서버 API key로 Model API `/api/v1/agents/incidents/step`을 호출한다.
6. agent·memory schema, request ID, incident ID, checksum과 안전 상수를 검사한다.
7. 응답 memory를 revision·parent hash compare-and-swap으로 저장한다.
8. agent의 `analysis`를 기존 confirmation gate 검증과 BFF 화면 DTO로 투영한다.
9. AI 원본, agent 원본과 화면 DTO snapshot을 analysis ID로 보존한 뒤 FE에 반환한다.

같은 최신 입력으로 agent가 `analysis=null`과 `NO_NEW_OBSERVATION`을 반환하면 BE는 새 위험
결과를 만들지 않는다. 같은 incident의 마지막 권위 analysis snapshot을 사용하고 현재
request ID만 응답 추적 값으로 갱신한다. 이전 snapshot이 없으면 422
`MODEL_CONTRACT_VIOLATION`으로 fail-closed한다.

현재 snapshot 저장소는 #9의 영구 DB 전환 전까지 프로세스 메모리를 사용한다. 서버 재시작
후 보존되는 기록으로 간주하면 안 된다. 현장 confirmation도 #9의 DB 전환 전까지 같은
제약을 가지며, 재시작 시 저장소가 비어 Rule gate가 다시 잠긴다. 세부 정책은
`docs/CONFIRMATION_GATE.md`와 `docs/INCIDENT_AGENT_MEMORY.md`를 따른다.

## 오류 매핑

| 상황 | HTTP | BFF code | retryable |
| --- | ---: | --- | ---: |
| FE 입력 오류 | 400 | `INVALID_REQUEST` | false |
| Model API 계약 불일치 | 422 | `MODEL_CONTRACT_VIOLATION` | false |
| Model API 연결·준비 장애 | 503 | `MODEL_SERVICE_UNAVAILABLE` | 원인에 따름 |
| 전체 응답 timeout | 504 | `MODEL_TIMEOUT` | true |
| agent memory 경합 | 409 | `AGENT_MEMORY_CONFLICT` | false |
| agent 안전 검증 실패 | 500 | `AGENT_SAFETY_FAILURE` | false |
| agent 도구 실행 실패 | 503 | `AGENT_EXECUTION_FAILED` | true |
| analysis ID 중복 | 409 | `INCIDENT_REFERENCE_CONFLICT` | false |

모든 오류는 `chemicheck119-dashboard-bff-v1` envelope와 `resetAllowed=false`를 사용하므로
FE는 실패 시 현재 대화와 분석 화면을 유지한다.
