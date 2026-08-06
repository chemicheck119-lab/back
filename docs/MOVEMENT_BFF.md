# Movement BFF

`POST /api/c2guard/v1/incidents/{incidentId}/movement`는 FE의 최신 GPS를 검증하고
provider-neutral 지도 상태를 반환한다. 이 요청은 사고 분석이나 AI agent를 다시 실행하지 않는다.

## 현재 구현 범위

- 서명된 `CHEMICHECK119_SESSION`과 incident scope 검사
- 위도·경도·정확도·관측 시각·source·journey state 검증
- 사고별 `clientSequence` 단조 증가 보장과 역행·중복 409 처리
- 분석 요청에서 검증된 사고 좌표와 출동소 표시명만 movement context로 보존
- 5분 초과 GPS에서 `POSITION_STALE`과 빈 geometry/ETA 반환
- 도착 상태에서 `ARRIVED`와 빈 geometry/ETA 반환
- 네이버 Maps Directions 5 서버 API를 실제 도로 경로 provider로 지원
- provider 미구성·장애·경로 없음에서 `ROUTE_UNAVAILABLE`을 반환하고 직선 경로를 만들지 않음
- provider route가 현재 위치·사고 위치에서 1.5km를 벗어나면
  `ROUTE_ENDPOINT_MISMATCH`로 geometry/ETA를 숨김
- RFC 7946 `LineString` 좌표를 `[longitude, latitude]` 순서로 반환
- 위험·확산 모델이 없음을 `NOT_COMPUTED_NO_VALIDATED_DISPERSION_MODEL`로 명시

응답의 `nextRefreshSeconds` 기본값은 5초이며 3~60초 범위에서만 설정할 수 있다.
`routeRecalculated=true`는 검증된 provider 경로가 새로 반환된 경우에만 사용한다.

## FE 요청

```http
POST /api/c2guard/v1/incidents/INC-20260801-0001/movement
Content-Type: application/json
X-Request-Id: REQ-MOVE-0017
Cookie: CHEMICHECK119_SESSION=...
```

```json
{
  "responderPosition": {
    "latitude": 37.2065,
    "longitude": 126.8311,
    "observedAt": "2026-08-01T14:22:00+09:00",
    "source": "MDT_DEVICE_GPS",
    "accuracyM": 12.0
  },
  "journeyState": "EN_ROUTE",
  "clientSequence": 17
}
```

`clientSequence`는 사고별로 반드시 증가시킨다. 앱 재시도에서 같은 값을 다시 보내면
`409 MOVEMENT_SEQUENCE_CONFLICT`가 반환되므로 최신 sequence를 사용해 새 관측값을 보낸다.

## FE 표시 규칙

| route.status | FE 동작 |
|---|---|
| `AVAILABLE` | attribution과 함께 실제 경로·거리·ETA 표시 |
| `DEMO_SIMULATION` | 실제 경로가 아님을 명시해 표시 |
| `ROUTE_UNAVAILABLE` | 기존 화면을 유지하고 경로·ETA 없음 표시 |
| `INCIDENT_LOCATION_REQUIRED` | 사고 위치 확인 필요 표시 |
| `POSITION_STALE` | 새 GPS를 받은 뒤 재요청 |
| `ROUTE_ENDPOINT_MISMATCH` | 잘못된 경로를 그리지 않고 재탐색 대기 |
| `ARRIVED` | 경로·ETA를 숨기고 현장 도착 상태 표시 |

모든 unavailable 상태에서 `geometry`, `etaSeconds`, `progressRatio`는 `null`이다. FE는 이 값을
임의의 직선 경로나 추정 ETA로 대체하면 안 된다.

## Provider adapter 경계

`RouteProvider`는 출발·도착 좌표와 요청 시각을 받아 provider provenance, GeoJSON 좌표,
전체·잔여 거리, 전체·잔여 시간, traffic 적용 여부와 attribution을 반환한다. provider가 없는
기본 구성은 항상 `ROUTE_UNAVAILABLE`이다. `DEMO_SIMULATION`은 기본적으로 차단되며 명시적인
비운영 환경에서만 `CHEMICHECK119_MOVEMENT_ALLOW_DEMO_SIMULATION=true`로 허용한다.

네이버 adapter는 `start`/`goal`을 `경도,위도` 순서로 전송하고, `traoptimal` 응답의 실시간
교통 반영 경로를 `LIVE_API`로 변환한다. 자격증명은 서버 전용 Secret으로만 주입하며 FE나
일반 로그에 노출하지 않는다.

```text
CHEMICHECK119_NAVER_DIRECTIONS_ENABLED=true
CHEMICHECK119_NAVER_DIRECTIONS_CLIENT_ID=<Secret Manager 참조>
CHEMICHECK119_NAVER_DIRECTIONS_CLIENT_SECRET=<Secret Manager 참조>
```

provider가 반환한 도로 경로의 첫 좌표와 마지막 좌표는 각각 요청 위치와 사고 위치에서
1.5km 이내여야 한다. 401/403과 잘못된 계약은 비재시도 실패, 429·5xx·통신 장애는 재시도
가능 실패로 분류하지만 단일 movement 요청 안에서 자동 재호출하지는 않는다. 다음 GPS
주기의 새 요청으로 재탐색한다.

## 현재 보존 한계

movement context와 sequence는 아직 프로세스 메모리에 있다. 재시작 보존과 위치 보존·삭제 정책은
#9의 승인된 DB 설계에 포함한다. 신고 원문이나 정밀 위치는 일반 로그에 기록하지 않는다.
