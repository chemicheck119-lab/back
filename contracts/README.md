# 케미체크119 BE 계약 스냅샷

이 디렉터리는 FE가 호출하는 BE/BFF v1 계약과 BE가 호출하는 모델 API 계약을 고정합니다.

- `dashboard-bff-v1.openapi.json`: BE가 소유하는 FE 공개 계약
- `incident-intake-replay-v1.openapi.json`: 공개 합성 지령 SSE·IncidentEnvelope 계약
- `synthetic-demo-logs-v1.openapi.json`: 전국 소방서별 합성 분석·대응 로그 조회 계약
- `upstream/model-api-v1.openapi.json`: `chemicheck119/llm` 모델 API 읽기 전용 스냅샷
- `upstream/model-api-integration-v1.json`: 저장소 경계·agent memory 소유권 계약
- `examples/bff/*.json`: FE·BE consumer fixture
- `examples/model/*.json`: BE→AI 요청 fixture
- `contract-lock.json`: 원본 PR·commit·SHA-256과 BE 로컬 결정

공개 합성 지령 계약은 실제 119 연계가 아닙니다. `PUBLIC_SYNTHETIC` 입력을 배포된
FE → BE → AI 경로로 처리해 adapter 경계를 검증하며 기본 설정에서는 비활성화됩니다.

대시보드 계약 원본은 `chemicheck119/llm` PR #31 merge commit
`e24fa93d538229844af4377976ee5a180c881fb3`이고, 사고 agent와 외부 memory 계약은 PR #44
merge commit `32736a680d445acea158da42efebd87651ce11b2`입니다. BE 계약은 다음 호환 결정을
추가합니다.

- AI-backed `incidents/analyze`와 `substances/discover`가 15초 안에 응답하지 않으면
  HTTP 504와 `MODEL_TIMEOUT`, `retryable=true`, 기존 request ID를 반환합니다.
- FE 사고분석 경로는 유지하되 내부 기본 호출은 AI `/api/v1/agents/incidents/step`을 사용합니다.
- agent memory는 BE가 소유하고 `revision`, `memory_sha256`, `parent_memory_sha256`으로
  compare-and-swap합니다. 현재 process-local adapter의 영속 DB 교체는 #9 범위입니다.

계약을 갱신할 때는 upstream commit을 먼저 고정하고 fixture와 SHA-256을 함께 갱신한 뒤
`BffContractSnapshotTest`를 실행합니다. BE 구현 편의를 위해 안전 필드나 오류 상태를
삭제해서는 안 됩니다.
