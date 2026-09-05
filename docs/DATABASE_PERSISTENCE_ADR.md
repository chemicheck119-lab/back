# ADR: 사고 상태 영속 저장소

## 결정

운영 저장소는 PostgreSQL을 사용하고 schema는 Flyway migration으로 관리한다. BE 저장소 계층은
Spring JDBC transaction을 사용한다. 로컬·CI에서는 PostgreSQL 호환 모드 H2를 사용하지만,
staging·production은 `CHEMICHECK119_REQUIRE_EXTERNAL_DATABASE=true`로 외부 PostgreSQL이
아니면 readiness를 `DOWN`으로 만든다.

JDBC를 선택한 이유는 agent memory compare-and-swap, confirmation revision lock, movement
sequence 단조 증가, response record의 다중 snapshot 결합을 SQL constraint와 transaction으로
직접 검증하기 위해서다.

## 저장 모델

- `incidents`: 사고 aggregate와 마지막 기록 활동 시각
- `incident_agent_memories`: AI agent memory revision·부모 checksum·event snapshot
- `incident_analysis_snapshots`: AI 원본, 검증된 agent 응답, FE용 BFF 응답 전체 JSON과 분석에
  사용한 역할별 confirmation ID. evidence와 model/data/rule/schema provenance도 원형으로
  보존한다.
- `substance_confirmations`와 `incident_confirmation_heads`: 인증 사용자 확인 이력과 사고·역할별
  활성 revision
- `incident_movement_contexts`와 `incident_movement_states`: 기록 시점에 결합할 최신 위치 상태
- `response_records` 및 하위 message/reference/snapshot table: 대화와 권위 ID를 단일 transaction으로
  결합한 최종 대응기록

`POST /api/c2guard/v1/incidents/{incidentId}/record`는 같은 사고에 속하고 현재 활성
confirmation 집합과 결합된 서버 저장 analysis만 참조한다. FE가 AI snapshot을 다시 보내도
권위 데이터로 저장하지 않는다. correction 전 snapshot·confirmation은 감사 이력으로 남지만
새 결과와 결합할 수 없다. 동일 사용자·사고·대화·참조 묶음은 fingerprint unique constraint로
멱등 처리한다.

## 환경변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `CHEMICHECK119_DATABASE_URL` | PostgreSQL 모드 in-memory H2 | JDBC URL |
| `CHEMICHECK119_DATABASE_USERNAME` | `sa` | DB 사용자 |
| `CHEMICHECK119_DATABASE_PASSWORD` | 없음 | 운영에서는 Secret Manager로만 주입 |
| `CHEMICHECK119_DATABASE_MAX_POOL_SIZE` | `5` | Hikari 최대 연결 수 |
| `CHEMICHECK119_DATABASE_MIN_IDLE` | `0` | 유휴 연결 수 |
| `CHEMICHECK119_DATABASE_CONNECTION_TIMEOUT_MS` | `3000` | 연결 획득 제한 시간 |
| `CHEMICHECK119_REQUIRE_EXTERNAL_DATABASE` | `false` | cloud 환경에서 `true` 필수 |

H2 기본값은 개발 편의를 위한 것이며 영속 저장 완료의 증거가 아니다. Cloud Run revision에는
반드시 PostgreSQL URL·사용자·비밀번호와 `CHEMICHECK119_REQUIRE_EXTERNAL_DATABASE=true`를
주입한다.

## 보존·보안

- DB credential은 Secret Manager 고정 버전을 사용하고 GitHub·image·로그에 저장하지 않는다.
- staging/production DB 연결은 TLS를 강제하고 Cloud SQL private IP 또는 connector/IAM 범위로
  제한한다. 애플리케이션 계정에는 migration/runtime에 필요한 최소 권한만 부여한다.
- 원문 대화는 response record 저장 요청에 포함된 범위만 보존한다.
- movement는 record 저장 시점의 최신 snapshot만 결합하고 GPS 이력을 무기한 축적하지 않는다.
- 사용자·조직·request ID·분석 및 확인 ID·모델 응답 provenance를 함께 보존한다.
- 로그에는 원문 대화, CAS 확인자 식별자, GPS 좌표, DB credential을 출력하지 않는다.
- 실제 보존기간·법적 보존 근거·삭제 승인 주체가 확정되기 전에는 자동 삭제 job을 활성화하지
  않는다. 이 결정 전 production 개인정보 수집은 허용하지 않으며 staging에는 합성 데이터만 쓴다.
- Cloud SQL 암호화·백업·PITR·접근 IAM과 개인정보 파기 정책은 staging DB 생성 승인 시 확정한다.

## 배포 gate

Cloud SQL 또는 승인된 PostgreSQL이 준비되기 전에는 DB를 요구하는 새 revision으로 staging
트래픽을 전환하지 않는다. migration, DB health, 재시작 복구, 백업 복원 rehearsal을 통과한
뒤 `GCP_MAX_INSTANCES` 상향과 production canary를 허용한다.

기존 `POST /api/c2guard/records`는 권위 ID와 snapshot을 표현하지 못하므로 새 영속 모델에
저장하지 않는다. legacy GET projection은 FE의 v1 전환과 별도 release decision 뒤에 제거한다.
