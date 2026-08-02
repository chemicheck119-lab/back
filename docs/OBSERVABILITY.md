# BE 관측성 기준

이 문서는 BFF 요청과 Model API 호출을 운영 로그에서 추적하는 최소 기준을 정의한다.
사용자 입력 원문, 물질 검색어, 사고·업체 식별자, GPS, API Key, Cookie와 session token은
로그나 metric tag에 기록하지 않는다.

## 요청 추적

`/api/**` 요청은 유효한 `X-Request-Id`를 그대로 사용하고, 없거나 형식이 잘못되면 BE가
새 값을 만든다. 같은 값은 응답 header, BFF 종료 로그, Model API 요청 header와 Model API
호출 로그에 사용한다.

BFF 종료 로그 event는 `bff_http_request`다. 다음의 제한된 field만 기록한다.

- `requestId`, 표준화된 HTTP `method`
- 고정 목록으로 분류된 `route`
- HTTP `status`, `outcome`, `durationMs`

동적 incident ID나 알 수 없는 실제 URI는 route에 사용하지 않는다. 알 수 없는 경로와
method는 각각 `unmatched`, `OTHER`로 집계한다.

Model API 로그 event는 `model_api_call`이다. 성공한 업무 API 호출은 INFO, health/meta 성공은
DEBUG, 최종 실패는 WARN으로 기록한다. 실제 URL 대신 고정 `operation`을 쓰며 request/response
body나 인증 header는 기록하지 않는다. 지연시간은 retry 대기를 포함한 전체 호출 시간이다.

## Metric

Actuator의 in-process Micrometer registry에는 다음 low-cardinality meter를 기록한다.

- `chemicheck119.bff.request.duration`
- `chemicheck119.bff.request.errors`
- `chemicheck119.model.api.duration`
- `chemicheck119.model.api.errors`

tag는 고정된 `route`/`operation`, `outcome`, 제한된 `attempts`만 사용한다. `/actuator/metrics`는
공개 Cloud Run 서비스에서 노출하지 않는다. Cloud Monitoring exporter 또는 log-based metric과
alert policy 연결은 별도 운영 변경으로 수행한다.

## 배포 식별

`GET /actuator/info`는 `application`과 `release` 정보를 반환한다. Cloud Run staging workflow는
`CHEMICHECK119_RELEASE_GIT_COMMIT`과 `CHEMICHECK119_RELEASE_ENVIRONMENT=staging`을 주입하고,
candidate와 승격된 stable URL의 smoke test에서 실제 커밋이 일치하는지 확인한다. Secret이나
인프라 식별 정보는 이 endpoint에 넣지 않는다.
