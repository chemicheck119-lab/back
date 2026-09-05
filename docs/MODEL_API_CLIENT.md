# Model API client 운영 계약

BE는 AI Model API를 호출할 때 `ModelApiClient`를 단일 진입점으로 사용한다. 보호된
`POST /api/v1/**` 요청에는 `X-API-Key`와 `X-Request-Id`를 함께 전달하고, health와
metadata 조회에는 request ID만 전달한다. API key가 없으면 보호 API 호출과 BE readiness를
실패 처리하여 인증 없이 요청이 나가지 않게 한다.

## 환경변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `CHEMICHECK119_MODEL_API_BASE_URL` | `http://localhost:8000` | AI Model API 주소 |
| `CHEMICHECK119_MODEL_API_KEY` | 없음 | 보호 API용 shared secret. 운영 환경에서는 필수 |
| `CHEMICHECK119_MODEL_API_IAM_ENABLED` | `false` | Cloud Run 서비스 간 IAM ID 토큰 인증 사용 여부 |
| `CHEMICHECK119_MODEL_API_IAM_AUDIENCE` | Model API 주소 | ID 토큰의 audience. 비어 있으면 `BASE_URL` 사용 |
| `CHEMICHECK119_MODEL_API_SCHEMA` | `chemiguard119-api-v1` | BFF가 허용하는 응답 schema version |
| `CHEMICHECK119_MODEL_API_CONNECT_TIMEOUT_SECONDS` | `2` | 연결 제한 시간(초) |
| `CHEMICHECK119_MODEL_API_RESPONSE_TIMEOUT_SECONDS` | `15` | 응답 제한 시간(초) |
| `CHEMICHECK119_MODEL_API_MAX_RETRIES` | `1` | 자동 재시도 횟수. 기본값을 초과해 늘리지 않는다 |

## 오류와 재시도

- 연결 실패와 `retryable=true`인 HTTP 503만 최대 1회 자동 재시도한다.
- 응답 timeout은 `MODEL_TIMEOUT`, `retryable=true`로 변환하지만 중복 실행을 막기 위해
  자동 재시도하지 않는다.
- HTTP 401, 422, 500과 schema 불일치는 재시도하지 않는다.
- AI가 반환한 `error.code`, `error.message`, `error.fields`, `request_id`,
  `occurred_at_utc`는 구조화된
  `ModelApiException`으로 보존한다.
- 로그에는 method, path, request ID, 상태, 오류 코드, 실행 시간, 시도 횟수만 남긴다.
  API key와 request/response body는 기록하지 않는다.

## 상태 확인

- AI 원본 probe: `GET /health/live`, `GET /health/ready`
- BE liveness: `GET /actuator/health/liveness`
- BE readiness: `GET /actuator/health/readiness`

BE readiness는 API key 설정과 AI `/health/ready` 응답을 함께 확인한다. AI가 준비되지
않았거나 호출에 실패하면 readiness는 `DOWN`이다.
