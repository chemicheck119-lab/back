# 인증된 Speech 전사 BFF

## 상태와 목적

- 사실 상태: **부분 구현 또는 개발용 데모**
- 구현 범위: Pad/브라우저가 Speech API Secret을 갖지 않도록 인증된 BE가 제한된 WAV를
  전달하고, 응답 계약과 안전 책임 경계를 검증한 뒤 필요한 필드만 반환
- 아직 검증하지 않은 범위: 실제 Pad 녹음 연동, 배포 환경 Cloud Run IAM, 운영 부하,
  현장 무전 정확도, 사용자 A/B test

이 경로는 음성을 전사할 뿐 물질을 식별하거나 CAS를 확인하거나 위험을 판단하지 않습니다.
전사문과 segment 품질 신호는 대응자가 검토해야 하며, 품질 신호는 정답 확률이 아닙니다.

## 호출 흐름

```text
Pad/FE
  └─ signed HttpOnly session
      ├─ 신고 접수 전: BE POST /api/c2guard/v1/transcriptions
      └─ 사고 생성 후 + incident scope
          └─ BE POST /api/c2guard/v1/incidents/{incidentId}/transcriptions
          ├─ media type·16 MiB hard limit·RIFF/WAVE header 검증
          ├─ 동일 X-Request-Id 전달
          └─ private Speech API POST /api/v1/transcriptions
              ├─ X-API-Key
              └─ 선택적 Cloud Run IAM ID token
```

BE는 원본 음성과 전사문을 DB·파일·로그에 저장하지 않습니다. Speech API도 요청 처리용
임시 파일을 제거하며 `audio_retained=false`를 반환해야 합니다. 이 값이 달라지거나 알려지지
않은 응답 필드, schema/request ID drift, hotword 사용, CAS·위험 판단 수행 표시가 있으면
BE는 422로 fail closed합니다.

접수 전 경로는 유효한 로그인 세션만 검사하고 응답 `incidentId=null`을 반환합니다. FE는
검토한 전사문을 별도의 사고 분석 요청에 넣고, 사고 ID는 BE 분석 경로가 발급하게 해야 합니다.
접수 전 임의 ID를 만들어 incident scope 검증을 우회하지 않습니다. 사고 생성 후 경로는 기존처럼
해당 incident scope까지 검사합니다.

## 요청 제한

| 항목 | 허용 범위 |
|---|---|
| Content-Type | `audio/wav`, `audio/x-wav`, `audio/wave` |
| 파일 크기 | 1 byte 초과, 최대 16 MiB |
| 컨테이너 사전 검사 | 12 byte 이상, `RIFF` + `WAVE` |
| Speech API 전체 검사 | PCM WAV, 16-bit, mono/stereo, 8–48 kHz, 최대 60초 |
| 동시 추론 | Speech API single inference semaphore |
| BE 자동 재시도 | 없음. 동일 음성의 중복 고비용 추론 방지 |

RIFF header만 통과한 잘못된 WAV는 Speech API의 전체 parser가 거부합니다. BE의 최대 파일
크기는 Speech API hard limit보다 높게 설정할 수 없습니다.

## 환경변수

| 환경변수 | 기본값 | 의미 |
|---|---:|---|
| `CHEMICHECK119_SPEECH_API_BASE_URL` | `http://localhost:8081` | Speech API origin |
| `CHEMICHECK119_SPEECH_API_KEY` | 빈 값 | 하위 API key, 없으면 호출 전 차단 |
| `CHEMICHECK119_SPEECH_API_IAM_ENABLED` | `false` | Cloud Run IAM ID token 사용 여부 |
| `CHEMICHECK119_SPEECH_API_IAM_AUDIENCE` | base URL | ID token audience |
| `CHEMICHECK119_SPEECH_API_CONNECT_TIMEOUT_SECONDS` | `2` | 연결 timeout |
| `CHEMICHECK119_SPEECH_API_RESPONSE_TIMEOUT_SECONDS` | `45` | 전체 응답 timeout |
| `CHEMICHECK119_SPEECH_API_MAX_AUDIO_BYTES` | `16777216` | 요청 상한 |
| `CHEMICHECK119_SPEECH_API_MAX_RESPONSE_BYTES` | `2097152` | 응답 상한 |

운영 배포에서는 Speech API를 비공개 Cloud Run으로 두고 BE service account에만 invoker
권한을 주는 구성이 목표입니다. 이는 아직 배포 검증 전이므로 운영 경험이나 고가용성
성과로 표현하지 않습니다.

## 오류와 관측성

`SPEECH_BUSY`와 `SPEECH_TIMEOUT`은 재시도 가능하지만 BE가 자동 재시도하지 않습니다.
FE가 동일 request ID를 유지한 채 사용자의 명시적 재시도를 수행해야 합니다. 계약·인증
오류는 재시도하지 않습니다.

metric은 operation/outcome/error kind만 사용합니다. 파일명, 전사문, 음성 bytes, API key,
IAM token, incident ID는 metric tag나 구조화 로그에 넣지 않습니다.

## 검증

```bash
./gradlew test --tests 'com.c2guard.*speech*' --no-daemon --console=plain
./gradlew test --tests com.c2guard.contract.BffContractSnapshotTest --no-daemon --console=plain
```

테스트는 생성한 최소 WAV byte와 비민감 fixture만 사용하며 외부 Speech API에 접속하지
않습니다.
