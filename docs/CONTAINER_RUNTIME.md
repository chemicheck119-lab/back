# BE 컨테이너 실행 계약

이 문서는 케미체크119 BE의 재현 가능한 이미지 빌드와 health probe 경계를 정의한다. 현재 범위는
이미지 빌드·로컬/CI smoke까지이며 운영 배포나 클라우드 리소스 생성은 포함하지 않는다.

## 이미지 구성

- build: Eclipse Temurin Java 17 JDK, multi-stage `bootJar`
- runtime: Eclipse Temurin Java 17 JRE
- runtime user: `10001:10001` (`c2guard`)
- application port: `8080`
- artifact: `/app/app.jar`
- OCI revision: build 시 `BUILD_REVISION`으로 주입

두 Temurin 이미지는 Docker Official Image의 multi-platform manifest digest로 고정한다. base image를
갱신할 때는 공식 tag의 새 digest와 Java 버전을 확인하고 PR의 Docker smoke를 통과시킨다.

## 빌드

```bash
docker build \
  --build-arg BUILD_REVISION="$(git rev-parse HEAD)" \
  --tag chemicheck119-be:local \
  .
```

Docker build context에는 Git 이력, 로컬 build 결과, `.env`, 문서와 계약 fixture를 포함하지 않는다.
Gradle wrapper는 Spring Boot 3.3의 지원 범위인 8.14.5로 고정하고 distribution SHA-256을 검증한다.

## 실행

아래 값은 예시 이름뿐이며 실제 Secret 값을 명령 이력이나 저장소에 기록하지 않는다.

```bash
docker run --rm \
  --publish 8080:8080 \
  --env CHEMICHECK119_SESSION_SECRET \
  --env CHEMICHECK119_MODEL_API_BASE_URL \
  --env CHEMICHECK119_MODEL_API_KEY \
  chemicheck119-be:local
```

Secret은 실행 시 승인된 Secret Manager 또는 배포 플랫폼에서 주입한다. Dockerfile, image layer,
GitHub Actions 인자, image label에 Secret을 넣지 않는다.

## Liveness와 readiness

Docker `HEALTHCHECK`는 다음 liveness endpoint만 사용한다.

```text
GET /actuator/health/liveness
```

liveness는 JVM과 Spring process가 요청에 응답할 수 있는지를 나타낸다. AI, 지도, DB 장애 때문에
컨테이너를 반복 재시작하지 않도록 외부 의존성을 포함하지 않는다.

트래픽 수신 가능 여부는 다음 readiness endpoint로 별도 판단한다.

```text
GET /actuator/health/readiness
```

현재 readiness에는 `modelApi`, `bffSecurity`가 포함된다. Model API Key나 안전한 session secret이
없으면 readiness는 `DOWN`이어야 한다. 지도 live provider와 DB가 확정되면 각각의 health indicator를
readiness에 추가한다. health 응답에는 Secret이나 내부 URL을 노출하지 않는다.

## CI 검증

`Backend CI / Docker image build and smoke` job은 Gradle 테스트가 성공한 뒤 다음을 확인한다.

1. 고정된 Java 17 base image로 clean Docker build
2. image user가 `10001:10001`인지 확인
3. OCI revision label이 Git commit과 일치하는지 확인
4. 컨테이너 내부 실제 UID가 10001인지 확인
5. Docker healthcheck와 liveness가 `UP`인지 확인
6. Model API Key가 없는 smoke 환경에서 readiness가 `DOWN`인지 확인

CI는 image를 registry에 push하거나 배포하지 않으며 운영 Secret을 사용하지 않는다.
