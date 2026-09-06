# Cloud Run staging 배포

BE staging은 기존 GCP 프로젝트 `chemi-check`의 서울 리전(`asia-northeast3`)에서 실행한다.
Model API preview와 같은 Artifact Registry 및 runtime service account를 재사용하되, BE 세션
시크릿은 별도의 Secret Manager secret으로 분리한다.

## 현재 서비스

| 항목 | 값 |
|---|---|
| Cloud Run service | `chemicheck119-be-staging` |
| Stable URL | `https://chemicheck119-be-staging-w6s6lwanpa-du.a.run.app` |
| Model API | `https://chemicheck119-model-api-preview-w6s6lwanpa-du.a.run.app` |
| Container repository | `asia-northeast3-docker.pkg.dev/chemi-check/chemicheck119/be` |
| Runtime service account | `chemicheck119-runtime@chemi-check.iam.gserviceaccount.com` |

Cloud Run 서비스는 공개 invoke를 허용한다. 운영 파일럿 모드에서는 분석·confirmation·movement·
record·session API를 모두 인증하고, 서명된 session cookie가 없으면 `401 AUTH_REQUIRED`로
거부한다. Firebase Hosting의 Cloud Run rewrite는 `__session` cookie만 원본 서비스로 전달하므로
staging 배포 스크립트가 cookie 이름을 `__session`으로 고정한다. Model API Key와 session signing
secret은 Cloud Run 환경변수의 평문 값이 아니라 Secret Manager의 고정 버전을 참조한다.

현재 Model API는 `development` 환경의 `competition-preview` release tier이며
`expert_reviewed=false`다. BFF service 이름의 `staging`은 BE 실행 환경을 뜻할 뿐, 연결된 AI
artifact가 reviewed staging이라는 뜻이 아니다. 현 구성은 공모전·내부 QA용 개발 preview로만
설명하고 현장 파일럿이나 상용 운영 근거로 사용하지 않는다.

## GitHub repository variables

`chemicheck119-lab/back`에 다음 Actions variables를 등록한다. 비밀 값은 GitHub에 복사하지
않고 Secret Manager 리소스 이름과 버전만 저장한다.

| 이름 | staging 값 |
|---|---|
| `GCP_PROJECT_ID` | `chemi-check` |
| `GCP_REGION` | `asia-northeast3` |
| `GCP_ARTIFACT_REPOSITORY` | `chemicheck119` |
| `GCP_CLOUD_RUN_SERVICE` | `chemicheck119-be-staging` |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | BE 전용 provider 전체 resource name |
| `GCP_DEPLOY_SERVICE_ACCOUNT` | `chemicheck119-github-deploy@chemi-check.iam.gserviceaccount.com` |
| `GCP_RUNTIME_SERVICE_ACCOUNT` | `chemicheck119-runtime@chemi-check.iam.gserviceaccount.com` |
| `GCP_MODEL_API_BASE_URL` | Model API preview stable URL |
| `GCP_MODEL_API_KEY_SECRET` | `chemicheck119-model-api-key` |
| `GCP_MODEL_API_KEY_SECRET_VERSION` | `1` |
| `GCP_SPEECH_API_BASE_URL` | private Speech API preview stable URL |
| `GCP_SPEECH_API_KEY_SECRET` | `chemicheck119-speech-api-key` |
| `GCP_SPEECH_API_KEY_SECRET_VERSION` | 고정된 활성 Secret version |
| `GCP_SESSION_SECRET` | `chemicheck119-be-session-secret-staging` |
| `GCP_SESSION_SECRET_VERSION` | `1` |
| `GCP_DATABASE_URL_SECRET` | 승인된 PostgreSQL JDBC URL secret 이름 |
| `GCP_DATABASE_URL_SECRET_VERSION` | 고정 secret version |
| `GCP_DATABASE_USERNAME_SECRET` | DB 사용자 secret 이름 |
| `GCP_DATABASE_USERNAME_SECRET_VERSION` | 고정 secret version |
| `GCP_DATABASE_PASSWORD_SECRET` | DB 비밀번호 secret 이름 |
| `GCP_DATABASE_PASSWORD_SECRET_VERSION` | 고정 secret version |
| `GCP_VPC_NETWORK` | `default` |
| `GCP_VPC_SUBNETWORK` | `default` |
| `GCP_VPC_EGRESS` | `private-ranges-only` |
| `GCP_CORS_ALLOWED_ORIGINS` | `https://chemicheck119.site` |
| `GCP_STAGING_AUTH_CALLBACK_URL` | `https://chemicheck119.site` 또는 승인된 callback path |
| `GCP_STAGING_AUTH_USER_ID` | 합성 staging 계정 ID |
| `GCP_STAGING_AUTH_STATION_ID` | 변경하지 않는 합성 station ID |
| `GCP_STAGING_AUTH_STATION_DISPLAY_NAME` | 화면 표시용 합성 소방서명 |
| `GCP_STAGING_AUTH_PASSWORD_SECRET` | staging password secret 이름 |
| `GCP_STAGING_AUTH_PASSWORD_SECRET_VERSION` | 고정 secret version |
| `GCP_MIN_INSTANCES` | `0` |
| `GCP_MAX_INSTANCES` | `1` |

Workload Identity provider는 `chemicheck119-lab/back`의 `refs/heads/develop`만 허용한다.
AI 저장소의 provider 조건을 넓히지 않고 BE provider를 별도로 사용한다.

## 배포와 롤백

Actions에서 `Backend Cloud Run staging deployment`를 `develop` ref로 선택하고
`confirm_staging=true`, `public_analysis_enabled=true`,
`public_incident_replay_enabled=true`, `public_synthetic_confirmation_enabled=true`로 실행한다.
staging은 승인된 Cloud SQL과
Direct VPC egress를 항상 사용하며 메모리 DB 배포를 허용하지 않는다. workflow는 다음 순서를
강제한다.

운영형 수동 접수 파일럿은 같은 출처 Hosting proxy와 staging 계정을 준비한 뒤
`staging_auth_enabled=true`, `public_analysis_enabled=false`,
`public_incident_replay_enabled=false`, `public_synthetic_confirmation_enabled=false`로 배포한다.
대회·QA에서 비밀번호 없는 제한 관할 진입이 필요할 때만
`public_pilot_access_enabled=true`를 추가한다.
이 모드에서는 분석·현장 확인·이동·기록 저장을 모두 서명 세션으로 보호한다.

1. `develop` commit 테스트
2. commit SHA를 OCI revision label로 포함한 non-root 이미지 빌드
3. Artifact Registry push 후 `image@sha256` digest 확정
4. 새 Cloud Run revision을 `--no-traffic` candidate tag로 배포
5. candidate URL에서 AI·세션 readiness, liveness, 공개 물질 검색·실제 사고 분석·공개 합성
   지령 SSE의 출처/PII 경계 검사
6. 새 revision으로 트래픽 100% 원자 전환
7. stable URL 재검사; 실패하면 직전 revision으로 자동 롤백

Cloud Run revision은 immutable하므로 이전 revision으로 수동 복구할 때는 다음처럼 트래픽만
되돌린다.

```bash
gcloud run services update-traffic chemicheck119-be-staging \
  --project chemi-check \
  --region asia-northeast3 \
  --to-revisions PREVIOUS_REVISION=100
```

## 운영 전환 차단 조건

confirmation, incident memory, analysis, movement state와 v1 record는 PostgreSQL/Flyway 저장
계층으로 전환됐다. staging 배포는 PostgreSQL credential secret과 Direct VPC egress 설정을
강제한다. H2는 로컬·단위 테스트에만 사용하며 Cloud Run staging에는 배포하지 않는다.

이 workflow는 공유 DB의 부하·동시성·복원 검증이 끝날 때까지 staging 전용이고
`GCP_MAX_INSTANCES=1`만 허용한다.
운영 무중단 배포와 canary traffic split은 다음 조건을 모두 충족한 뒤 별도 production workflow로
만든다.

- Cloud SQL 백업·PITR·복원 rehearsal과 migration rollback 절차 승인
- 인증 adapter에서 실제 session 발급 및 만료·폐기 E2E 검증
- 실제 FE HTTPS origin을 CORS allowlist에 등록
- 후보 revision에서 서명 세션을 이용한 BE→AI 사고 분석 smoke 추가
- Cloud Monitoring 알림과 rollback 운영 책임자 확정
