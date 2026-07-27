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

## 다음에 할 일 (TODO)

1. Python 버전(`rule_engine_draft.py`)이랑 같은 입력값 넣어서 결과 똑같이 나오는지 대조 테스트
2. 요청 값 검증(400 처리) 추가
3. RDS 연동 — 지금은 CSV를 메모리에 올리는 방식인데, `서버와_배포.pdf`에서 배운 대로 나중에 JPA + RDS로 교체
4. 프론트(준희님)랑 API 명세서 맞춰보기
