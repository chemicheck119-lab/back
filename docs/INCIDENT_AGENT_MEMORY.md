# 사고 에이전트 외부 memory 경계

## 책임과 호출

FE 공개 경로는 `POST /api/c2guard/v1/incidents/analyze`로 유지합니다. BE는 내부에서 AI
`POST /api/v1/agents/incidents/step`을 호출하며 요청은 다음 구조입니다.

```json
{
  "analysis": {},
  "memory": {},
  "max_actions": 6
}
```

첫 호출에는 `memory`가 없고 후속 호출에는 같은 incident의 최신 memory 원본을 그대로
전달합니다. BE는 매 요청마다 서버 저장소의 현재 INCIDENT·FACILITY confirmation을
`analysis`에 다시 넣습니다. `memory_can_trigger_rule=false`이므로 memory에 과거 확인 상태가
있어도 Rule 실행 권한으로 사용하지 않습니다.

## 검증과 compare-and-swap

BE는 agent 응답을 저장하기 전에 다음을 검증합니다.

- agent schema `chemicheck119-incident-agent-v1`
- memory schema `chemicheck119-incident-agent-memory-v1`
- 동일 request ID·incident ID·run ID·status·pending inputs
- `memory_can_trigger_rule=false`
- `autonomous_risk_decision_allowed=false`
- `trace_is_chain_of_thought=false`
- Python canonical JSON 규칙과 동일한 SHA-256 checksum

첫 memory는 `revision=1`, `parent_memory_sha256=null`만 허용합니다. 이후에는 새 revision이
현재 revision보다 정확히 1 크고 새 parent hash가 현재 `memory_sha256`과 같을 때만 원자적으로
교체합니다. 같은 parent를 사용한 동시 응답 중 먼저 저장된 하나만 성공하며 나머지는 409
`AGENT_MEMORY_CONFLICT`입니다.

agent `events`는 PLAN·ACT·OBSERVE·REPLAN의 구조화 도구 감사정보이지 chain-of-thought가
아닙니다. 현재는 내부 snapshot에만 저장하며 FE 응답이나 일반 로그로 내보내지 않습니다.

## 상태와 실패

- `WAITING_FOR_HUMAN`: memory를 저장하고 확인 대기 BFF DTO를 반환합니다.
- `GOAL_COMPLETED`: memory·분석 snapshot을 저장하고 안전 검증된 BFF DTO를 반환합니다.
- `PARTIAL_MAX_ACTIONS`: 반환된 analysis가 있으면 현재 상태로 보존합니다. 자동 무한 호출은
  하지 않습니다.
- `FAILED_RETRYABLE`: memory·events 감사정보를 보존하고 503
  `AGENT_EXECUTION_FAILED`, `retryable=true`를 반환합니다.
- `FAILED_SAFETY`: memory·events 감사정보를 보존하되 분석을 표시하지 않고 500
  `AGENT_SAFETY_FAILURE`를 반환합니다.

새 관찰이 없어 `analysis=null`이면 마지막 권위 BFF snapshot을 반환합니다. snapshot이 없는
상태에서 null 분석을 받으면 계약 오류로 처리합니다.

## 현재 저장 한계

현재 `IncidentAgentMemoryStore`, `IncidentAnalysisSnapshotStore`, `ConfirmationStore`는 모두
process-local입니다. 서버 재시작 시 memory·분석·확인이 함께 사라지며 확인 gate가 다시
잠기는 fail-closed 상태로 돌아갑니다. 이는 운영 영속 저장 완료가 아닙니다.

#9에서 DB 제품·보존기간·암호화·migration 결정 후 동일 저장 port를 영구 adapter로
교체해야 합니다. 승인 없이 클라우드 DB나 Secret을 생성하지 않습니다.
