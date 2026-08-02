# 공개 합성 119 지령 Replay

## 목적

현재 실제 119 지령 feed는 연결되어 있지 않습니다. 이 기능은 공식 연계를 사칭하지 않으면서
기관 adapter가 들어갈 Incident Intake 경계와 실시간 전달 방식을 공개 시연에서 검증합니다.

```text
공개 합성 시나리오
→ BE IncidentEnvelope 생성
→ SSE incident.accepted
→ FE 신고 입력 반영
→ 기존 BE/BFF 사고 분석
→ 실제 AI 호출
```

입력은 `PUBLIC_SYNTHETIC`이지만 처리 경로는 배포된 FE → BE → AI입니다. 실제 분석 장애 시
저장된 성공 결과로 전환하지 않습니다.

## API

```http
GET /api/c2guard/v1/intake/replay-stream/CONTEST-LIVE-CHEMICAL-001
Accept: text/event-stream
```

성공하면 약 1초 뒤 `incident.accepted` 이벤트 한 건을 보내고 연결을 닫습니다. `data`는
`contracts/incident-intake-replay-v1.openapi.json`의 `IncidentEnvelope`입니다.

반드시 표시해야 하는 경계 필드는 다음과 같습니다.

- `sourceType=SYNTHETIC_DISPATCH_REPLAY`
- `dataClassification=PUBLIC_SYNTHETIC`
- `containsPersonalInformation=false`
- `sourceProvider=CHEMICHECK119_PUBLIC_REPLAY`
- 실제 119 신고가 아니라는 `disclosure`

## 설정

기본값은 비활성화입니다.

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `CHEMICHECK119_INCIDENT_REPLAY_ENABLED` | `false` | replay Controller 활성화 |
| `CHEMICHECK119_INCIDENT_REPLAY_PUBLIC_ENDPOINT_ENABLED` | `false` | 세션 없는 공개 GET 허용 |
| `CHEMICHECK119_INCIDENT_REPLAY_DELAY` | `1s` | 지령 도착 연출 지연 |
| `CHEMICHECK119_INCIDENT_REPLAY_TIMEOUT` | `10s` | SSE 연결 timeout |

공개 endpoint는 개인정보가 없는 고정 시나리오를 읽는 GET만 제공합니다. 임의 신고 내용을
주입하는 공개 POST endpoint는 제공하지 않습니다.

## 운영 안전장치

- 두 활성화 flag를 명시하지 않으면 anonymous 접근이 차단됩니다.
- 등록되지 않은 scenario는 기본 시나리오로 fallback하지 않고 404입니다.
- 응답과 로그는 request ID와 source event ID만 남기며 신고자 정보가 없습니다.
- live 분석 장애를 replay 성공으로 위장하지 않습니다.
- 향후 `AUTHORIZED_DISPATCH`는 별도 서버 간 인증·기관 승인·DB idempotency·보존 정책을
  갖춘 provider adapter로 구현해야 합니다.

## 공개 데이터 근거의 의미

[소방안전 빅데이터 플랫폼](https://bigdata-119.kr/)의 공개 데이터는 시나리오 구조와 대회
활용 맥락을 참고하기 위한 것입니다. 이 replay가 실시간 119 원천 데이터 또는 소방기관이
제공한 신고라고 의미하지 않습니다.
