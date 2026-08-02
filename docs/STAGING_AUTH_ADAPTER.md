# Staging 인증 어댑터

## 목적과 경계

운영 SSO·인증 Gateway가 준비되기 전 `https://chemicheck119.site`의 실제 BFF 연동을 검증하기
위한 합성 테스트 계정 전용 adapter다. 기본값은 비활성화이며 production 사용자·실제 출동정보를
저장하지 않는다.

공개 파일럿 시작 URL과 승인 계정 로그인 URL은 분리한다.

```text
공개 파일럿: POST https://chemicheck119.site/auth/staging/pilot
승인 계정 로그인: GET/POST https://chemicheck119.site/auth/staging/login
```

Firebase Hosting이 `/auth/**`와 `/api/**`를 BE Cloud Run으로 rewrite한다. Cloud Run stable URL을
브라우저에서 직접 사용하면 `run.app`과 `chemicheck119.site` 사이의 cross-site cookie가 되므로
운영 파일럿의 브라우저 계약은 같은 출처인 `chemicheck119.site`만 사용한다.

## 흐름

1. FE가 `/auth/staging/pilot/stations`에서 소방청 공개 좌표 기반 관할 목록을 조회한다.
2. 사용자가 지역·소방서를 선택하면 FE가 같은 출처의 `/auth/staging/pilot`에 허용된 `stationId`를 POST한다.
3. BE는 callback과 동일한 HTTPS Origin과 서버 카탈로그의 `stationId`를 검사한다.
4. 제한된 user·선택 station·role을 담은 HS256 `__session` cookie를 발급한다.
5. 서버 allowlist의 고정 HTTPS callback으로만 303 redirect한다.
6. FE는 `GET /api/c2guard/v1/session`으로 station·공개 위치·권한을 확인한다.
7. `POST /api/c2guard/v1/logout` 성공 후 시작 화면으로 이동한다.

`public-pilot-enabled=false`인 일반 staging 로그인은 기존처럼 CSRF token, Secret Manager password,
constant-time 비교, client 주소별 실패 제한을 사용한다.

callback은 요청 parameter로 받지 않으므로 open redirect를 허용하지 않는다. 로그인 HTML은
`no-store`, CSP `form-action 'self'`, `frame-ancestors 'none'`, `no-referrer`를 적용한다.

## 환경변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `CHEMICHECK119_STAGING_AUTH_ENABLED` | `false` | staging에서만 `true` |
| `CHEMICHECK119_STAGING_AUTH_PUBLIC_PILOT_ENABLED` | `false` | 대회·QA용 공개 파일럿 POST 허용 |
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
파일럿 시작: POST https://chemicheck119.site/auth/staging/pilot
승인 계정 로그인: GET/POST https://chemicheck119.site/auth/staging/login
callback: https://chemicheck119.site
session: GET https://chemicheck119.site/api/c2guard/v1/session
logout: POST https://chemicheck119.site/api/c2guard/v1/logout
cookie: __session; Path=/; HttpOnly; Secure; SameSite=Lax; 기본 만료 8시간
```

세션 응답의 `stationId`는 DB·권한에 사용하는 안정 ID이고 `stationDisplayName`은 화면 표시값이다.
공개 파일럿 관할은 `stationLocation`에 주소·위도·경도·전화번호·공개 데이터 출처가 함께 반환된다.
FE는 사용자가 입력한 소방서명을 권한 정보로 사용하지 않는다.

공개 파일럿은 비밀번호 인증이 아니다. 대회·QA에서 계정 입력 없이 제한 관할 기능을 검증하기
위한 staging 전용 진입점이며, 실제 기관 사용자·권한·지령 시스템으로 표현하지 않는다.

Firebase Hosting은 Cloud Run rewrite 요청에서 일반 cookie를 제거하고 `__session`만 전달한다.
따라서 Hosting과 같은 출처로 공개하는 staging 배포는
`CHEMICHECK119_SESSION_COOKIE_NAME=__session`을 강제한다. Cloud Run을 직접 호출하는 로컬·계약
테스트의 기본 cookie 이름 `CHEMICHECK119_SESSION`은 그대로 유지한다.

## 운영 전환

운영 인증 adapter가 준비되면 staging adapter를 끄고 같은 signed session claim과 session/logout
API 계약을 유지한다. 사용자별 즉시 폐기, MFA/SSO, 계정 lifecycle, 실제 incident assignment는
운영 인증 시스템의 책임이다.
