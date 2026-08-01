# 사고분석 BFF 연동

FE는 `POST /api/c2guard/v1/incidents/analyze`만 호출하고 AI `/api/v1/**` 경로나
`X-API-Key`를 사용하지 않는다. BE는 다음 순서로 요청을 처리한다.

1. FE의 `X-Request-Id`를 검증하거나 새 ID를 생성한다.
2. BFF camelCase 요청을 검증하고 Model API snake_case 요청으로 변환한다.
3. 같은 request ID와 서버 API key로 Model API를 호출한다.
4. 모델 응답의 schema, request ID, incident ID와 confirmation gate를 검사한다.
5. 확인 전 위험 필드를 만들지 않고 BFF 화면 DTO로 투영한다.
6. AI 원본과 화면 DTO snapshot을 analysis ID로 보존한 뒤 FE에 반환한다.

현재 snapshot 저장소는 #9의 영구 DB 전환 전까지 프로세스 메모리를 사용한다. 서버 재시작
후 보존되는 기록으로 간주하면 안 된다. 현장 confirmation은 #6, 사용자 세션과 사고 접근
권한은 #5에서 연결한다. 따라서 두 이슈가 병합되기 전 이 endpoint를 운영 공개하지 않는다.

## 오류 매핑

| 상황 | HTTP | BFF code | retryable |
| --- | ---: | --- | ---: |
| FE 입력 오류 | 400 | `INVALID_REQUEST` | false |
| Model API 계약 불일치 | 422 | `MODEL_CONTRACT_VIOLATION` | false |
| Model API 연결·준비 장애 | 503 | `MODEL_SERVICE_UNAVAILABLE` | 원인에 따름 |
| 전체 응답 timeout | 504 | `MODEL_TIMEOUT` | true |
| analysis ID 중복 | 409 | `INCIDENT_REFERENCE_CONFLICT` | false |

모든 오류는 `chemicheck119-dashboard-bff-v1` envelope와 `resetAllowed=false`를 사용하므로
FE는 실패 시 현재 대화와 분석 화면을 유지한다.
