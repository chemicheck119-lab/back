# 비용·트래픽·보안 검증과 운영 경계

2026-10-18. 로컬 구현·검증이며 운영 배포 또는 실제 사용자 부하 검증이 아니다.

## 문제에서 변경까지

해커톤 제출 뒤 예상하지 못한 비용이 발생했다는 사용자 설명에서 출발했다. 실제 청구는 GCP console의 서비스/SKU별 표와 실행 설정을 대조했다. 금융 내역·계정 식별자는 공개 저장소에 넣지 않으며 비공개 증거 폴더에 보관했다. 요청 증가와 유지 비용을 구분해야 한다. 이번 변경으로 과거 청구가 줄었다고 주장하지 않는다.

추가 발견: 브라우저의 retryable 503 재요청과 BFF Model API 재시도가 겹칠 수 있었다. 응답을 못 받았다고 공급자가 계산하지 않은 것은 아니므로, 유료 추론을 유발하는 POST의 자동 재전송을 양쪽에서 막았다. 읽기 GET의 기존 제한 재시도는 유지한다. timeout 중단은 공급자의 계산·청구 취소를 보장하지 않는다.

## 선택한 최소 조치

- 신규 인프라 없이 MVC interceptor에서 고비용 POST 입장을 제한한다. 인증/사고 접근은 기존 Spring Security가 소유한다.
- 프로세스당 동시 처리 기본 2건, 서명된 조직+사용자별 rolling 60초 30건, 전체 60초 120건.
- 대기열을 무제한으로 쌓지 않고 429 `CAPACITY_LIMITED`, `Retry-After: 60`, `retryable=false`를 반환한다. 사용자가 재요청 여부를 결정한다.
- 익명 분석 모드가 명시적으로 켜진 경우 모든 익명 사용자는 하나의 quota를 공유한다. X-Forwarded-For나 클라이언트 사용자 ID로 quota를 우회하지 않는다.
- quota map은 최대 1024명으로 제한하며 60초 지난 항목을 제거한다. 처리 완료·오류 후 permit을 반환한다. 거절은 새 모델 호출을 만들지 않는다.
- 적용 경로: POST analyze/brief/discover, 접수 전·사고별 transcriptions. 전화 ingress나 SSE·일반 record 경로의 rate control을 구현한 것은 아니다.
- 출력에는 토큰·전사문·IP·모델 payload를 추가로 기록하지 않는다. 원래 승인 revision 및 두 CAS 게이트를 유지한다.

```sh
export CHEMICHECK119_COST_ADMISSION_ENABLED=true
export CHEMICHECK119_COST_MAX_IN_FLIGHT=2
export CHEMICHECK119_COST_PER_PRINCIPAL_PER_MINUTE=30
export CHEMICHECK119_COST_GLOBAL_PER_MINUTE=120
export CHEMICHECK119_COST_MAX_PRINCIPALS=1024
```

수치는 실습용 초기 정책이다. 사람 수용량 또는 비용 최적값을 측정해 정한 수치가 아니다. 프로세스 재시작 시 quota가 초기화되며 여러 인스턴스 간 공유되지 않는다. 실제 배포에서는 service-level max instances, 공급자별 사용 한도와 운영 시간을 함께 제한해야 한다. 다중 인스턴스의 일관된 제한이 필요해질 때만 공유 저장소/edge limiter를 검토한다. Redis를 먼저 추가하지 않는다.

## 동일 조건 전후 검증

`CostAdmissionIntegrationTest`: MockMvc와 로컬 fake provider, 동일 POST/본문/16 worker. 기준선은 admission만 끄고, 변경 후에는 켠다. accepted 작업이 점유 중인 상태를 latch로 고정해 timing 우연에 의존하지 않는다.

| 항목 | 제한 없음 | 제한 적용 |
|---|---:|---:|
| 제출 요청 | 16 | 16 |
| 가짜 upstream 호출 | 16 | 2 |
| 429 거절 | 0 | 14 |
| 실제 모델/통화 호출 | 0 | 0 |

완료 후 새 요청 200, peak fake provider concurrency 2를 확인했다. 이것은 요청량을 줄여 수용 범위를 지킨 실험이며 처리 성능 개선·동시 사용자 16명 지원·실제 청구 절감을 뜻하지 않는다. SQL, TLS, TCP, 네트워크 지연, Cloud Run autoscaler가 포함된 부하 시험이 아니다.

추가 검증: 개인 quota 초과, 사용자 바꿔도 전체 quota 유지, quota 메모리 상한, 시간 경과 복구, 오류 permit 반환, 인증 누락 401·다른 사고 SSE 403, 기존 CORS/전사 승인/두 CAS 검증, FE POST 재시도 방지, Model API POST 503·연결 실패 재시도 방지.

```sh
./gradlew clean test --no-daemon --console=plain
# burst 원본: build/reports/cost-security/burst.json
# 전체 검사 결과: build/test-results/test/TEST-*.xml
cd gateway
npm run check
```

## 제한·실패에서 사용자에게 보여주는 것

429를 안전 판정이나 빈 분석 결과로 바꾸지 않는다. 입력을 유지하고 처리 한도 도달을 알린다. 인증 문제는 401, 다른 사고 접근은 403, upstream 장애는 기존 오류 계약으로 구분한다. 공급자 오류에서 데모 응답을 만들어 제공하지 않는다.

## 아직 운영 완료가 아닌 이유

- 새 admission/retry 정책은 로컬과 Draft PR에만 있으며 운영에 배포하지 않았다.
- 공급자 통화/LLM의 실제 결제 내역과 GCP 비용을 합친 총액은 미확정이다.
- admission은 금액 상한·DDoS 방어·인증 시스템을 대체하지 않는다. 전체 HTTP body 크기, SSE 구독 수, 전화 길이/공급자 과금 한도는 별도 운영 경계가 필요하다.
- GCP 모델·음성 IAM 및 SQL 설정은 읽기 전용 확인했다. 실제 침투 테스트나 인증 우회 공격을 운영에 실행하지 않았다.
- SQL TLS 강제·storage 상한·공개 파일럿 계정 범위는 운영 변경과 호환성/복구 검증이 필요하다. 실제 사고 데이터 공개 사용 준비 완료로 표현하지 않는다.
- budgets의 알림은 hard cap이 아니다. 별도 spend cap은 지원 서비스/갱신 지연/잔여 저장비 등을 확인해야 하며 이 작업에서 설정하지 않았다.

본인 기여 기록: 사용자가 문제와 요구사항을 제시했고, 이번 코드·시험·감사 수행은 AI가 담당했다. 사용자의 기존 ClawOps 연결 작업과 이번 구현을 합쳐 직접 개발 경력으로 기술하지 않는다.
