# Backend 안전 상태 전이 평가

## 목적

confirmation 정정 전 분석이 현재 상태와 섞여 저장되거나, 동일 요청 재시도로 기록이
중복되는 회귀를 한 흐름에서 검사합니다. 개별 단위 테스트의 개수를 성능처럼 제시하지 않고,
실제 BFF confirmation·record endpoint와 영속 저장소를 연결한 결과를 JSON으로 남깁니다.

```text
INCIDENT confirmation 저장
→ 동일 confirmation 재요청: 기존 revision 유지
→ FACILITY confirmation 저장
→ 두 활성 confirmation ID와 old analysis snapshot 결합
→ INCIDENT confirmation 정정: old revision SUPERSEDED
→ old analysis 저장 시도: 409 fail-closed
→ 새 활성 confirmation pair와 fresh analysis 결합
→ response record 저장
→ 같은 본문 exact retry: 기존 record ID 반환, child row 중복 없음
```

## 재현

기본 실행은 보고서를 `build/reports/evaluation/backend-safety-state-v1.json`에 생성합니다.

```bash
./gradlew test \
  --tests 'com.c2guard.bff.record.BackendSafetyStateEvaluationTest' \
  --rerun-tasks \
  --no-daemon \
  --console=plain
```

Git에 포함되지 않는 별도 경로가 필요하면 환경변수로 지정합니다.

```bash
CHEMICHECK119_BACKEND_SAFETY_REPORT=/approved/private/path/report.json \
./gradlew test \
  --tests 'com.c2guard.bff.record.BackendSafetyStateEvaluationTest' \
  --rerun-tasks \
  --no-daemon \
  --console=plain
```

## 2026-09-07 측정 결과

| 검증 항목 | 결과 |
|---|---:|
| 전체 deterministic check | 18/18 통과 |
| 동일 confirmation의 추가 revision | 0건 |
| 정정 전 analysis의 record 저장 | 0건 |
| stale 저장 응답 | `409 INCIDENT_REFERENCE_CONFLICT` |
| fresh record | 1건 |
| exact retry 뒤 record | 1건 유지 |
| exact retry 뒤 message | 2건 유지 |
| exact retry 뒤 analysis reference | 1건 유지 |
| exact retry 뒤 confirmation reference | 2건 유지 |

- report SHA-256: `deca81276b8d339d9be9940e6b3cccefa2af89f5c992bbb1616bb5c5074cc25e`
- scenario SHA-256: `c5e47855033aa98c7e810bbe7fd6702a9e8c0428167bbc41bee0d2567a336bd5`
- evaluator source SHA-256: `0cd0677c8855fc7ec24d62b1c06417b0be06ab217605a41b383ee223b1867636`
- Flyway V4 SHA-256: `2974d084c070cf8ebf46f173638cfbaad8ac5d57954be5edb032213b9aa77248`

같은 코드와 scenario로 두 번 실행했을 때 report SHA-256이 일치했습니다. 시각·latency처럼
실행마다 달라지는 값을 보고서에서 제외하고 key 순서를 고정했기 때문입니다.

## 사실 상태와 주장 한계

| 상태 | 범위 |
|---|---|
| 구현 완료 | confirmation 멱등성·append-only 정정, snapshot binding, stale 저장 차단, record exact retry |
| 부분 구현 또는 개발용 데모 | H2 PostgreSQL 호환 모드의 단일 프로세스 18-check 통합 회귀 |
| 설계 완료·구현 전 | Cloud SQL PostgreSQL의 동시 요청·transaction lock·재시작 복구 평가 |
| 검증되지 않은 가설 | 상용 고가용성, 실제 장애 빈도, 현장 안전성 개선 |

analysis snapshot은 이 평가에서 Model API 호출 대신 서버 저장소에 고정 fixture로 주입합니다.
따라서 FE→BE→Model API 전체 E2E나 실제 PostgreSQL 동시성 결과로 표현하면 안 됩니다.
