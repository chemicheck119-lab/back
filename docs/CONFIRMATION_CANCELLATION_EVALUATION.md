# confirmation 취소 안전 상태 전이 평가

## 검증 질문

두 CAS가 확인된 뒤 한쪽 확인을 취소하면 과거 충돌 결과가 현재 상태와 섞이지 않고,
다음 분석·대응 기록이 다시 한쪽 확인 상태로 돌아가는가?

```text
INCIDENT + FACILITY confirmation 활성
→ 두 ID에 결합된 analysis snapshot 생성
→ exact FACILITY ID 취소
→ 취소 감사 이벤트 보존 + exact retry 멱등 처리
→ 취소 전 analysis 저장 시도: 409 fail-closed
→ INCIDENT ID만 결합한 새 snapshot 생성
→ Rule 미실행·위험 표시 금지
→ 한쪽 확인 상태의 대응 기록 저장
```

## 재현

```bash
CHEMICHECK119_CONFIRMATION_CANCELLATION_REPORT=/approved/private/path/report.json \
./gradlew test \
  --tests 'com.c2guard.bff.record.ConfirmationCancellationSafetyEvaluationTest' \
  --rerun-tasks \
  --no-daemon \
  --console=plain
```

기본 출력은 `build/reports/evaluation/confirmation-cancellation-state-v1.json`입니다.
개인정보나 실제 현장 데이터는 사용하지 않으며 보고서는 Git에 포함하지 않습니다.

## 2026-09-08 v1 측정 결과

| 검증 항목 | 결과 |
|---|---:|
| deterministic check | 20/20 통과 |
| 취소 응답 | `200 CANCELLED` |
| 동일 취소 재시도 | 최초 시각·감사 이벤트 유지 |
| 활성 confirmation | 2개 → 1개 |
| 취소 전 analysis의 record 저장 | `409 INCIDENT_REFERENCE_CONFLICT` |
| 취소 뒤 FACILITY snapshot binding | `null` |
| 취소 뒤 Rule 실행 | `false` |
| 취소 뒤 위험 표시 허용 | `false` |
| 새 상태 record의 confirmation reference | 1개 |

- report SHA-256: `bda9205e48d7d5901d6f18f272e36b4315194ff2016e88bf972da96ad400cb94`
- scenario SHA-256: `eed0fecd381e7554e5294549bf715c97eb16c3ffad0821b148e545e87145e3b4`
- evaluator SHA-256: `0eb550d01e3dd05936b6e310d3c1b505c51e6fa48fc1a98614305061b998a399`
- Flyway V5 SHA-256: `ebbdb8d0f3760fc33908eec572f14f9cc0582abfdad48e66d51c3a883f6f755f`

같은 코드와 scenario로 두 번 실행한 report SHA-256이 일치했습니다. 실행 시각과 latency처럼
재실행마다 변하는 값은 보고서에 넣지 않았습니다.

## 사실 상태와 주장 한계

| 상태 | 범위 |
|---|---|
| 구현 완료 | 확인 취소 API, append-only 감사 이벤트, exact retry, stale ID 거부, 취소 뒤 재분석 요구 |
| 부분 구현 또는 개발용 데모 | H2 PostgreSQL 호환 모드의 단일 프로세스 20-check 상태 전이 평가 |
| 설계 완료·구현 전 | Cloud SQL migration·재시작 복구·다중 인스턴스 동시 취소 검증 |
| 검증되지 않은 가설 | 실제 현장의 취소 빈도·오입력 감소·안전성 또는 대응시간 개선 |

이 평가는 신고 음성, Parser, Resolver, Retriever 또는 실제 Model API를 호출하지 않습니다.
따라서 `음성→인계 전체 운영 E2E`, `현장 검증`, `Cloud SQL 고가용성 검증`으로 표현하면 안 됩니다.
GitHub Actions의 PostgreSQL job 통과 여부는 PR 증적으로 별도 기록합니다.
