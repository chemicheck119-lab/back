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
→ 공개 합성 전용 고정 물질 확인 1/2, 2/2
→ 실제 AI 재분석과 CAMEO 규칙 실행
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

3분 시연에서는 수신한 incidentId에만 다음 본문 없는 POST를 사용할 수 있습니다.

```http
POST /api/c2guard/v1/intake/replays/{incidentId}/confirmations/INCIDENT
POST /api/c2guard/v1/intake/replays/{incidentId}/confirmations/FACILITY
```

`INCIDENT`는 차아염소산나트륨(7681-52-9), `FACILITY`는 염산(7647-01-0)으로 서버에
고정되어 있습니다. 이는 실제 대원 확인이 아니라 `SYNTHETIC_DEMO_CONFIRMATION`이며, 응답과
FE 화면에 같은 고지를 표시해야 합니다. 호출자가 CAS·물질명·확인 근거를 선택할 수 없습니다.

반드시 표시해야 하는 경계 필드는 다음과 같습니다.

- `sourceType=SYNTHETIC_DISPATCH_REPLAY`
- `dataClassification=PUBLIC_SYNTHETIC`
- `containsPersonalInformation=false`
- `sourceProvider=CHEMICHECK119_PUBLIC_REPLAY`
- 실제 119 신고가 아니라는 `disclosure`

## 설정

기본값은 비활성화입니다.

Cloud Run staging workflow에서는 `public_incident_replay_enabled=true`와
`public_synthetic_confirmation_enabled=true`를 각각 명시해야 하며 candidate와 stable URL에서
SSE → 고정 확인 2건 → AI 재분석 → CAMEO 규칙 실행을 smoke test합니다.

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `CHEMICHECK119_INCIDENT_REPLAY_ENABLED` | `false` | replay Controller 활성화 |
| `CHEMICHECK119_INCIDENT_REPLAY_PUBLIC_ENDPOINT_ENABLED` | `false` | 세션 없는 공개 GET 허용 |
| `CHEMICHECK119_SYNTHETIC_CONFIRMATION_ENABLED` | `false` | 발급된 합성 incident의 고정 확인 POST 허용 |
| `CHEMICHECK119_SYNTHETIC_INCIDENT_TTL` | `30m` | 발급 incidentId 확인 가능 시간 |
| `CHEMICHECK119_MAX_ACTIVE_SYNTHETIC_INCIDENTS` | `100` | 인스턴스 메모리 등록 상한 |
| `CHEMICHECK119_INCIDENT_REPLAY_DELAY` | `1s` | 지령 도착 연출 지연 |
| `CHEMICHECK119_INCIDENT_REPLAY_TIMEOUT` | `10s` | SSE 연결 timeout |

`chemicheck119.site` 공개 파일럿처럼 BE가 발급한 서명 세션 안에서만 데모를 실행할 때는
`INCIDENT_REPLAY_ENABLED=true`, `INCIDENT_REPLAY_PUBLIC_ENDPOINT_ENABLED=false`,
`SYNTHETIC_CONFIRMATION_ENABLED=true` 조합을 사용한다. 이 조합은 replay GET과 합성 확인
POST를 모두 `/api/**` 기본 인증 정책 아래에 두며 익명 호출을 허용하지 않는다.
Cloud Run staging workflow에서는 `authenticated_demo_replay_enabled=true`로 같은 조합을
선택하며, 이 옵션은 `public_pilot_access_enabled=true`일 때만 허용된다.

공개 POST는 replay로 발급된 TTL 내 incidentId와 두 역할만 받으며 요청 본문은 읽지 않습니다.
임의 신고·CAS·물질명을 주입하는 endpoint는 제공하지 않습니다. 등록 정보는 staging 단일
인스턴스 메모리에만 있어 재시작 후에는 지령을 다시 수신해야 하고, 확인 레코드는 기존 승인된
PostgreSQL 저장소를 사용합니다.

## 운영 안전장치

- 세 활성화 flag를 명시하지 않으면 합성 확인의 anonymous 접근이 차단됩니다.
- 등록되지 않은 scenario는 기본 시나리오로 fallback하지 않고 404입니다.
- 응답과 로그는 request ID와 source event ID만 남기며 신고자 정보가 없습니다.
- live 분석 장애를 replay 성공으로 위장하지 않습니다.
- 향후 `AUTHORIZED_DISPATCH`는 별도 서버 간 인증·기관 승인·DB idempotency·보존 정책을
  갖춘 provider adapter로 구현해야 합니다.

## 공개 데이터 근거의 의미

[소방안전 빅데이터 플랫폼](https://bigdata-119.kr/)의 공개 데이터는 시나리오 구조와 대회
활용 맥락을 참고하기 위한 것입니다. 이 replay가 실시간 119 원천 데이터 또는 소방기관이
제공한 신고라고 의미하지 않습니다.
