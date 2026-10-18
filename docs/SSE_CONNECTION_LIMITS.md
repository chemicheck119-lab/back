# SSE 연결 제한과 통화 흐름 검증

2026-10-18. 로컬 합성 입력만 사용. 운영 변경·유료 전화·외부 모델 호출 없음.

## 변경 이유

전사 SSE는 연결이 유지되는 동안 서버 자원을 점유한다. 기존 45초 timeout과 replay 100건만으로 동시 연결 개수를 제한하지 못했다. 연결·반환을 하나의 lock으로 관리해 프로세스 전체 기본 32개, 사고당 4개를 제한한다. 이 값은 초기 정책이며 측정된 사용자 수용량이 아니다.

CHEMICHECK119_SSE_MAX_CONNECTIONS=32 (1..4096)
CHEMICHECK119_SSE_MAX_PER_INCIDENT=4 (1..64)

완료·timeout·error·전송 실패·DB replay 실패에서 슬롯을 반환하며 중복 콜백은 한 번만 반영한다. 마지막 구독자가 사라지면 사고 map entry도 제거한다. 네트워크 전송·DB 접근 중에는 전체 연결 lock을 잡지 않는다.

인증 및 사고 접근 확인 뒤, 초과 연결에 HTTP 204/no-store를 반환한다. 일반 POST의 429와 다른 이유는 native EventSource의 자동 재연결을 중단하기 위해서다. Retry-After는 수동 재시도 권고이며 EventSource가 적용하는 시간 제한이 아니다. 수락된 SSE의 reconnect 간격은 5초다. 근거: https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events

FE는 CLOSED를 재연결 중과 구분하고 수동 연결 버튼을 제공한다. EventSource에서 HTTP 상태를 읽을 수 없으므로 연결 한도 초과라고 단정하지 않는다. 기존 원문/수정문을 유지하고 같은 revision 재생으로 편집 중 문장을 덮어쓰지 않는다. 사용자 재연결 버튼 클릭을 강제로 60초 막지는 않는다.

## 실제 실행

1. 변경 전 Phone 테스트 통과: /tmp/chemicheck119-phone-baseline.log.
2. 변경 후 전체 ./gradlew clean test --no-daemon --console=plain 통과.
3. 실제 HTTP 서버(random port) + H2: 세션 생성 → 합성 provider call start → 최종 전사 POST 두 번 → DB 1행 → 수정 승인 revision 1 → SSE에서 수정문 수신 → 오래된 revision 수정 409.
4. 같은 HTTP 시험을 별도 PostgreSQL 17 컨테이너에서 통과. 테스트 DB product는 Flyway/PostgreSQL 로그와 실제 테이블 SQL로 확인. 처음 CHEMICHECK119_DATABASE_*만 설정한 시도는 test resources 설정 때문에 H2였으며 PostgreSQL 증거에서 제외했다. SPRING_DATASOURCE_*로 다시 실행했다.
5. 실제 HTTP 한도 초과 204, 인증 누락 401, 다른 사고 403. 단위 시험은 동시 시도 24개에서 설정 상한 3개만 수락, 종료 콜백 중복 회수, replay DB 실패 후 슬롯 회수를 확인.

```sh
./gradlew test --tests '*Phone*' --no-daemon --console=plain
# 별도 빈 로컬 PostgreSQL DB에서만 HTTP 시험 실행
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55439/sse_review \
SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=local-synthetic-only \
./gradlew test --tests '*PhoneStreamHttpTest' --rerun-tasks --no-daemon --console=plain
```

위 비밀번호는 격리 로컬 시험 전용 예시다. 실제 자격증명으로 재사용하지 않는다. HTTP fixture는 빈 DB를 전제로 한다. 재실행 시 별도 새 시험 DB를 사용한다. 운영 DB에서 실행하지 않는다.

## 남은 범위

브라우저 실제 EventSource/화면 E2E, 통신사 전화, 실제 모델 분석·결과 저장까지 한 번에 연결한 시연은 이번 HTTP 시험 범위가 아니다. FE 회귀는 mock API 기반이다. SSE replay/live 경합·100건 이상 복구·다중 인스턴스 전파·분산 quota는 별도 작업이다. 프로세스별 상한이며 DDoS 방어/금액 상한이 아니다. 원문 데이터 DB 저장과 별개로 접속 종료 감지는 timeout 또는 쓰기 실패까지 지연될 수 있다.

직접 수행 기록: 이번 코드와 시험은 AI가 수행했다. 현준의 첫 실습은 PhoneTranscriptEventBrokerTest를 직접 실행하고 상한/회수 결과를 설명하는 것이다.
