# Staging 인증 어댑터

## 목적과 경계

운영 SSO·인증 Gateway가 준비되기 전 `https://chemicheck119.site`의 실제 BFF 연동을 검증하기
위한 합성 테스트 계정 전용 adapter다. 기본값은 비활성화이며 production 사용자·실제 출동정보를
저장하지 않는다.

로그인 시작 URL은 다음과 같다.

```text
https://api.chemicheck119.site/auth/staging/login
```

custom domain 적용 전에는 BE Cloud Run stable URL 뒤에 `/auth/staging/login`을 붙일 수 있지만,
`run.app`과 `chemicheck119.site` 사이의 cross-site cookie 제한 때문에 브라우저별 동작을 보장하지
않는다. FE·BFF·인증을 같은 `chemicheck119.site` site 아래에 두는 구성이 기준이다.

## 흐름

1. FE가 설정된 로그인 시작 URL로 top-level 이동한다.
2. adapter가 HttpOnly CSRF cookie와 일회성 form token을 발급한다.
3. 서버 환경에 고정된 user ID와 Secret Manager password를 constant-time 비교한다.
4. 실패 횟수를 client 주소별로 제한하고 입력값·비밀번호를 로그에 남기지 않는다.
5. 성공 시 HS256 `CHEMICHECK119_SESSION` HttpOnly·Secure·SameSite=Lax cookie를 발급한다.
6. 서버 allowlist의 고정 HTTPS callback으로만 303 redirect한다.
7. FE는 `GET /api/c2guard/v1/session`으로 station과 권한을 확인한다.
8. `POST /api/c2guard/v1/logout` 성공 후 로그인 화면으로 이동한다.

callback은 요청 parameter로 받지 않으므로 open redirect를 허용하지 않는다. 로그인 HTML은
`no-store`, CSP `form-action 'self'`, `frame-ancestors 'none'`, `no-referrer`를 적용한다.

## 환경변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `CHEMICHECK119_STAGING_AUTH_ENABLED` | `false` | staging에서만 `true` |
| `CHEMICHECK119_STAGING_AUTH_CALLBACK_URL` | 없음 | 고정 HTTPS FE callback |
| `CHEMICHECK119_STAGING_AUTH_USER_ID` | 없음 | 합성 테스트 사용자 ID |
| `CHEMICHECK119_STAGING_AUTH_STATION_ID` | 없음 | 변경하지 않는 station ID |
| `CHEMICHECK119_STAGING_AUTH_STATION_DISPLAY_NAME` | 없음 | FE 표시 소방서명 |
| `CHEMICHECK119_STAGING_AUTH_PASSWORD` | 없음 | Secret Manager에서 주입, 최소 16바이트 |
| `CHEMICHECK119_STAGING_AUTH_ROLES` | `RESPONDER` | 허용 BFF role |
| `CHEMICHECK119_STAGING_AUTH_INCIDENT_SCOPES` | `*` | staging 합성 사고 scope |
| `CHEMICHECK119_STAGING_AUTH_CSRF_MAX_AGE` | `5m` | login form token 수명 |
| `CHEMICHECK119_STAGING_AUTH_LOCK_DURATION` | `10m` | 실패 횟수 window |
| `CHEMICHECK119_STAGING_AUTH_MAX_FAILED_ATTEMPTS` | `5` | window당 실패 상한 |

enabled인데 callback·계정·station·password가 불완전하면 `stagingAuth` readiness가 `DOWN`이다.

## FE 전달 계약

```text
FE origin: https://chemicheck119.site
로그인 시작: https://api.chemicheck119.site/auth/staging/login
callback: https://chemicheck119.site
session: GET https://api.chemicheck119.site/api/c2guard/v1/session
logout: POST https://api.chemicheck119.site/api/c2guard/v1/logout
cookie: CHEMICHECK119_SESSION; Path=/; HttpOnly; Secure; SameSite=Lax; 기본 만료 8시간
```

세션 응답의 `stationId`는 DB·권한에 사용하는 안정 ID이고 `stationDisplayName`은 화면 표시값이다.
FE는 사용자가 입력한 소방서명을 권한 정보로 사용하지 않는다.

## 운영 전환

운영 인증 adapter가 준비되면 staging adapter를 끄고 같은 signed session claim과 session/logout
API 계약을 유지한다. 사용자별 즉시 폐기, MFA/SSO, 계정 lifecycle, 실제 incident assignment는
운영 인증 시스템의 책임이다.
