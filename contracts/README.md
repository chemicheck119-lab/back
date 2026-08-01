# 케미체크119 BE 계약 스냅샷

이 디렉터리는 FE가 호출하는 BE/BFF v1 계약과 BE가 호출하는 모델 API 계약을 고정합니다.

- `dashboard-bff-v1.openapi.json`: BE가 소유하는 FE 공개 계약
- `upstream/model-api-v1.openapi.json`: `chemicheck119/llm` 모델 API 읽기 전용 스냅샷
- `examples/bff/*.json`: FE·BE consumer fixture
- `contract-lock.json`: 원본 PR·commit·SHA-256과 BE 로컬 결정

원본은 `chemicheck119/llm` PR #31의 merge commit
`e24fa93d538229844af4377976ee5a180c881fb3`입니다. BE 계약은 다음 한 가지 호환 결정을
추가했습니다.

- AI-backed `incidents/analyze`와 `substances/discover`가 15초 안에 응답하지 않으면
  HTTP 504와 `MODEL_TIMEOUT`, `retryable=true`, 기존 request ID를 반환합니다.

계약을 갱신할 때는 upstream commit을 먼저 고정하고 fixture와 SHA-256을 함께 갱신한 뒤
`BffContractSnapshotTest`를 실행합니다. BE 구현 편의를 위해 안전 필드나 오류 상태를
삭제해서는 안 됩니다.
