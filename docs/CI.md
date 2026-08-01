# BE 테스트와 계약 검증 CI

## 실행 시점

GitHub Actions `Backend CI`는 `develop`·`main` 대상 pull request와 두 브랜치의 push에서
실행됩니다. 기능 PR의 기준 브랜치는 `develop`이며 `main`은 별도 릴리스 승격 시 동일 검증을
다시 수행합니다.

`Gradle test and contract verification` job은 Java 17에서 다음 명령을 실행합니다.

```bash
./gradlew clean test --no-daemon --console=plain
```

현재 테스트에는 BFF Controller·사용자 인증·confirmation gate·AI client·agent memory CAS,
movement 입력·sequence·fail-closed 상태와 OpenAPI/fixture SHA drift 검사가 포함됩니다. 실제
Model API, 지도 API, 운영 DB에는 접속하지 않고 test fixture와 MockWebServer만 사용합니다.

## 공급망과 권한

- workflow 권한은 `contents: read`만 사용합니다.
- `pull_request_target`을 사용하지 않아 외부 PR 코드가 기본 브랜치 권한으로 실행되지 않습니다.
- 공식 GitHub/Gradle action은 Node.js 24 기반 릴리스의 검증된 commit SHA로 고정합니다.
- Gradle wrapper checksum·구조는 별도 wrapper validation action으로 확인합니다.
- CI에는 운영 API Key, 사용자 session Secret, 지도 Secret, 실제 신고·정밀 위치를 주입하지
  않습니다.

## 결과와 artifact

성공·실패와 관계없이 JUnit XML과 Gradle HTML test report를 7일간 artifact로 보존합니다.
테스트 데이터는 저장소의 비민감 fixture와 가짜 Secret만 사용합니다.

GitHub Actions check 이름은 `Backend CI / Gradle test and contract verification`입니다.
branch protection의 required check 지정은 이 workflow가 `develop`에서 안정적으로 실행된 것을
확인한 뒤 별도 저장소 설정으로 적용합니다.

## 아직 포함하지 않는 범위

- #8의 live provider·cache·rate limit·재탐색 임계값 테스트
- #9 선택된 DB migration·transaction·restart persistence 테스트
- Docker image build·non-root·healthcheck 검증
- staging의 실제 FE→BE→AI·지도·DB E2E

이 항목은 #8·#9 구현과 #10 후속 단계, #11 배포 검증에서 추가합니다.
