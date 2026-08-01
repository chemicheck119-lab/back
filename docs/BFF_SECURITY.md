# BFF 사용자 인증과 Client 보안 경계

## 결정

BE는 브라우저의 `CHEMICHECK119_SESSION` HttpOnly cookie에 담긴 HS256 JWT를 검증합니다.
FE에는 Model API Key 또는 session signing Secret을 전달하지 않습니다. 운영 세션 발급 진입점은
배포 환경의 신뢰된 인증 adapter가 소유합니다. 별도 인증 시스템이 준비되기 전 staging에는 기본
비활성화된 합성 테스트 계정 adapter를 사용할 수 있습니다. 운영 환경에서는 이를 활성화하지
않습니다.

공개 FE 통합 검증이 필요한 staging에서는 `CHEMICHECK119_PUBLIC_ANALYSIS_ENABLED=true`로
물질 후보 검색과 사고 분석만 세션 없이 허용할 수 있습니다. BE는 이때 분석 요청에만 고정된
`public-fe`/`public-staging` 감사 주체를 부여합니다. confirmation, movement, record, session과
나머지 `/api/**`는 계속 서명 세션을 요구합니다. 기본값은 `false`이며 운영 인증 정책을
대체하지 않습니다.

세션 payload는 다음 claim을 사용합니다.

| claim | 의미 |
|---|---|
| `iss`, `aud` | 허용된 발급자와 이 BFF audience |
| `sub` | 사용자 ID |
| `org` | 소속 ID |
| `station_name` | 화면에 표시할 신뢰된 소방서 이름 |
| `roles` | `RESPONDER`, `COMMANDER`, `ADMIN` |
| `incidents` | 접근 가능한 incident ID 목록 또는 발급자가 승인한 `*` scope |
| `jti`, `iat`, `exp` | session ID, 발급 시각, 만료 시각 |

`ADMIN` 또는 `incidents=["*"]`만 incident ID 없는 신규 분석을 시작할 수 있습니다. 일반
대응자는 서명된 `incidents` 목록에 포함된 사고만 분석할 수 있습니다. path에 incident ID가
있는 confirmation, movement, record 경로도 Controller 구현 여부와 관계없이 같은 정책을 먼저
적용합니다.

## HTTP 계약

- `GET /api/c2guard/v1/session`: 인증 사용자·고정 station ID·표시명·역할·사고 scope·만료 반환
- `POST /api/c2guard/v1/logout`: `CHEMICHECK119_SESSION`을 `Max-Age=0`으로 만료
- 인증 누락·서명 변조·만료·issuer/audience 불일치: HTTP 401, `AUTH_REQUIRED`
- 공개 분석 모드에서만 `POST /api/c2guard/v1/substances/discover`와
  `POST /api/c2guard/v1/incidents/analyze`는 세션 없이 호출 가능
- 인증 성공 후 incident scope 부족: HTTP 403, `ACCESS_DENIED`
- 두 오류 모두 `chemicheck119-dashboard-bff-v1`, 동일 request ID,
  `resetAllowed=false`를 반환합니다.
- `/api/**`의 레거시 경로도 전환 기간 동안 익명 호출을 허용하지 않습니다.
- liveness는 공개하되 readiness에는 `bffSecurity`를 포함합니다. 32바이트 이상의 signing
  Secret이 없으면 readiness가 DOWN입니다.

유효한 session 요청에는 동일 토큰을 만료 시각까지만 다시 설정합니다. 운영 기본 cookie는
`Path=/; HttpOnly; Secure; SameSite=Lax`이며, 변조된 cookie는 즉시 `Max-Age=0`으로
만료시킵니다. 응답이나 로그에는 토큰과 Secret을 기록하지 않습니다.

## CORS와 CSRF

운영 기본값에는 cross-origin 허용 주소가 없습니다. 실제 FE origin은
`CHEMICHECK119_CORS_ALLOWED_ORIGINS`에 comma-separated allowlist로 주입합니다. credential
CORS에서 wildcard origin은 거부하며 허용 header도 `Content-Type`, `X-Request-Id`,
`X-CSRF-Token`으로 제한합니다.

개발 origin과 insecure cookie는 `dev` profile에만 있습니다.

```bash
SPRING_PROFILES_ACTIVE=dev bash gradlew bootRun
```

API는 cookie 인증이지만 Spring의 form CSRF token은 사용하지 않습니다. 대신 모든 변경 BFF
Controller가 JSON만 소비하고, SameSite cookie와 credential CORS origin 검사를 함께
강제합니다. 브라우저가 다른 origin의 JSON 요청을 실행하지 못하도록 preflight allowlist를
자동화 테스트로 검증합니다.

FE와 BE가 다른 origin이면 FE `fetch`가 `credentials: "include"`를 사용해야 합니다.
현재 FE 저장소는 이번 BE 작업 범위에서 수정하지 않았으므로 cross-origin live E2E 전에 해당
변경과 인증 adapter가 필요합니다. same-origin 배포에서는 fetch 기본 credential 정책으로
cookie가 전달됩니다.

## 환경변수

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `CHEMICHECK119_SESSION_SECRET` | 없음 | HS256 key, UTF-8 기준 최소 32바이트 |
| `CHEMICHECK119_SESSION_ISSUER` | `chemicheck119-session-gateway` | 허용 issuer |
| `CHEMICHECK119_SESSION_AUDIENCE` | `chemicheck119-bff` | 허용 audience |
| `CHEMICHECK119_SESSION_MAX_AGE` | `8h` | 최대 session 수명, 상한 24시간 |
| `CHEMICHECK119_SESSION_CLOCK_SKEW` | `30s` | 시각 오차 허용, 상한 5분 |
| `CHEMICHECK119_SESSION_COOKIE_NAME` | `CHEMICHECK119_SESSION` | cookie 이름 |
| `CHEMICHECK119_SESSION_COOKIE_SECURE` | `true` | 운영 HTTPS cookie 강제 |
| `CHEMICHECK119_SESSION_COOKIE_SAME_SITE` | `Lax` | `Lax`, `Strict`, `None` |
| `CHEMICHECK119_CORS_ALLOWED_ORIGINS` | 없음 | 운영 FE origin allowlist |
| `CHEMICHECK119_PUBLIC_ANALYSIS_ENABLED` | `false` | staging 공개 FE의 검색·분석 두 API만 익명 허용 |

staging adapter 설정과 로그인 URL은
[`STAGING_AUTH_ADAPTER.md`](./STAGING_AUTH_ADAPTER.md)를 기준으로 합니다.

Secret 교체 시 기존 session은 모두 무효화됩니다. 즉시 사용자별 폐기가 필요한 환경에서는
신뢰된 인증 adapter가 짧은 만료와 별도의 revocation 정책을 함께 제공해야 합니다.

## 검증

```bash
bash gradlew clean test --no-daemon --console=plain
```

자동화 테스트는 서명 변조·만료·audience, 401·403, 레거시 보호, incident scope, cookie 보안
속성, 개발 CORS allowlist와 wildcard 미사용을 검증합니다.
