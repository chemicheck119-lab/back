# C2-Guard Backend (v0.2)

Python `rule_engine_draft.py`를 Spring Boot로 옮긴 버전.
위험물법 별표19 매트릭스 기반 물질 반응성 판정 API.

## 로컬 실행 방법

1. IntelliJ에서 이 폴더(`c2guard`)를 Gradle 프로젝트로 열기
   (File → Open → build.gradle 선택 → "Open as Project")
2. Gradle이 자동으로 의존성(Spring Boot, commons-csv) 다운로드할 때까지 대기
3. `C2guardApplication.java` 우클릭 → Run
4. 정상 기동되면 콘솔에 `Tomcat started on port 8080` 뜸

## 테스트 방법

터미널에서:
```bash
# 1) 물질 반응성 판정
curl -X POST http://localhost:8080/api/compatibility/check \
  -H "Content-Type: application/json" \
  -d '{"substanceA": "황산", "substanceB": "메탄올"}'

# 2) 시설 보유물질 조회
curl "http://localhost:8080/api/facilities/search?keyword=효성"

# 3) 시설+사고물질 통합 조회 (핵심 기능)
curl -X POST http://localhost:8080/api/incident-check \
  -H "Content-Type: application/json" \
  -d '{"facilityName": "(주)LG생활건강", "incidentSubstance": "톨루엔"}'
```

또는 Postman/Insomnia로 같은 요청 보내도 됨.

## 지금 상태 (v0.2)

- registry 202종 로딩 정상 동작 (Python 버전과 동일한 결과 나와야 함 — 대조 확인 필요)
- 유별 데이터가 없어서 대부분 `UNCLASSIFIED` 반환하는 게 정상
- 최현준 CAMEO 데이터 연동되면 `CompatibilityService`에 CAMEO 조회 우선순위 로직 추가 예정 (별표19는 fallback으로)

## BFF v1 계약

FE-BE-AI 통합의 공개 계약과 레거시 전환 정책은 다음 파일을 기준으로 합니다.

- `contracts/dashboard-bff-v1.openapi.json`
- `contracts/contract-lock.json`
- `contracts/examples/bff/`
- `docs/BFF_V1_API_CONTRACT.md`
- `docs/MODEL_API_CLIENT.md`
- `docs/INCIDENT_ANALYSIS_BFF.md`
- `docs/SUBSTANCE_DISCOVERY_BFF.md`
- `docs/BFF_SECURITY.md`
- `docs/CONFIRMATION_GATE.md`
- `docs/INCIDENT_AGENT_MEMORY.md`
- `docs/MOVEMENT_BFF.md`
- `docs/CI.md`

현재 `POST /api/c2guard/v1/incidents/analyze`는 FE 요청을 Model API 요청으로 변환하고,
모델 응답을 확인 gate가 적용된 화면 DTO로 투영합니다. 모든 `/api/**` 요청은 서명된
`CHEMICHECK119_SESSION` cookie를 요구하며 BFF 사고 경로는 session의 incident scope를
검사합니다. 현장 confirmation은 인증 사용자 기준 append-only revision으로 저장되며 다음
사고분석 호출에 같은 incident의 활성 확인만 주입합니다. 영구 저장(#9), 검증된 AI runtime과
배포 인증 adapter가 준비되기 전에는 운영 공개하지 않습니다.

`POST /api/c2guard/v1/substances/discover`는 FE 관찰·물질명 검색을 Model API 후보 검색으로
연결하며, 후보 없음·근거 미적재·현장 확인 필요 상태를 그대로 보존합니다.

`POST /api/c2guard/v1/incidents/{incidentId}/movement`는 FE GPS를 AI 호출과 분리해 처리하며,
stale 위치·sequence 충돌·미구성 지도 provider에서 경로와 ETA를 만들지 않는 fail-closed
상태를 반환합니다. 실제 도로 경로 adapter는 지도 사업자 결정(#7) 이후 연결합니다.

기능 구현은 `develop`에서 이슈별 feature 브랜치를 분기하고 PR base를 `develop`으로 사용합니다.

## 자동 검증

로컬에서는 다음 명령으로 unit·integration·계약 snapshot 테스트를 모두 실행합니다.

```bash
./gradlew clean test --no-daemon --console=plain
```

`develop`·`main` 대상 PR과 push에서는 GitHub Actions `Backend CI`가 같은 명령을 Java 17로
실행하고 Gradle wrapper와 계약 drift를 함께 검사합니다. CI에는 운영 Secret을 주입하거나
실제 AI·지도·DB를 호출하지 않습니다.

## 다음에 할 일 (TODO)

1. Python 버전(`rule_engine_draft.py`)이랑 같은 입력값 넣어서 결과 똑같이 나오는지 대조 테스트
2. 요청 값 검증(400 처리) 추가
3. RDS 연동 — 지금은 CSV를 메모리에 올리는 방식인데, `서버와_배포.pdf`에서 배운 대로 나중에 JPA + RDS로 교체
4. 프론트(준희님)랑 API 명세서 맞춰보기
