# Cloud Run staging 배포

BE staging은 기존 GCP 프로젝트 `chemi-check`의 서울 리전(`asia-northeast3`)에서 실행한다.
AI staging과 같은 Artifact Registry 및 runtime service account를 재사용하되, BE 세션 시크릿은
별도의 Secret Manager secret으로 분리한다.

## 현재 서비스

| 항목 | 값 |
|---|---|
| Cloud Run service | `chemicheck119-be-staging` |
| Stable URL | `https://chemicheck119-be-staging-w6s6lwanpa-du.a.run.app` |
| Model API | `https://chemicheck119-model-api-staging-w6s6lwanpa-du.a.run.app` |
| Container repository | `asia-northeast3-docker.pkg.dev/chemi-check/chemicheck119/be` |
| Runtime service account | `chemicheck119-runtime@chemi-check.iam.gserviceaccount.com` |

Cloud Run 서비스는 공개 invoke를 허용하지만 `/api/**`는 서명된 `CHEMICHECK119_SESSION`
cookie가 없으면 `401 AUTH_REQUIRED`로 거부한다. Model API Key와 session signing secret은
Cloud Run 환경변수의 평문 값이 아니라 Secret Manager의 고정 버전을 참조한다.

## GitHub repository variables

`chemicheck119/BE_Repository`에 다음 Actions variables를 등록한다. 비밀 값은 GitHub에 복사하지
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
| `GCP_MODEL_API_BASE_URL` | AI staging stable URL |
| `GCP_MODEL_API_KEY_SECRET` | `chemicheck119-model-api-key` |
| `GCP_MODEL_API_KEY_SECRET_VERSION` | `1` |
| `GCP_SESSION_SECRET` | `chemicheck119-be-session-secret-staging` |
| `GCP_SESSION_SECRET_VERSION` | `1` |
| `GCP_CORS_ALLOWED_ORIGINS` | 실제 HTTPS FE origin, 확정 전에는 미등록 |
| `GCP_MIN_INSTANCES` | `0` |
| `GCP_MAX_INSTANCES` | `1` |

Workload Identity provider는 `chemicheck119/BE_Repository`의 `refs/heads/develop`만 허용한다.
AI 저장소의 provider 조건을 넓히지 않고 BE provider를 별도로 사용한다.

## 배포와 롤백

Actions에서 `Backend Cloud Run staging deployment`를 `develop` ref로 선택하고
`confirm_staging=true`로 실행한다. workflow는 다음 순서를 강제한다.

1. `develop` commit 테스트
2. commit SHA를 OCI revision label로 포함한 non-root 이미지 빌드
3. Artifact Registry push 후 `image@sha256` digest 확정
4. 새 Cloud Run revision을 `--no-traffic` candidate tag로 배포
5. candidate URL에서 liveness, AI·세션 readiness, 익명 API 차단 검사
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

현재 confirmation, incident memory, movement state, record가 프로세스 메모리에 있다. 따라서
revision 전환 시 진행 중인 사고 상태가 새 instance로 승계되지 않으며, 여러 instance나 revision에
트래픽을 분할하면 같은 incident의 상태가 갈라질 수 있다.

이 workflow는 그 위험을 제한하기 위해 staging 전용이고 `GCP_MAX_INSTANCES=1`만 허용한다.
운영 무중단 배포와 canary traffic split은 다음 조건을 모두 충족한 뒤 별도 production workflow로
만든다.

- 사고·확정·이동·대응 기록을 공유 영속 저장소로 이전
- 인증 adapter에서 실제 session 발급 및 만료·폐기 E2E 검증
- 실제 FE HTTPS origin을 CORS allowlist에 등록
- 후보 revision에서 서명 세션을 이용한 BE→AI 사고 분석 smoke 추가
- Cloud Monitoring 알림과 rollback 운영 책임자 확정
