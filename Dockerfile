# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy@sha256:400014962ad7224461f945bb1cc3d7d5a1927ce15b8245b72d9cedcda554cd2a AS build

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle

RUN ./gradlew dependencies --no-daemon --console=plain

COPY src ./src

RUN ./gradlew clean bootJar --no-daemon --console=plain

FROM eclipse-temurin:17-jre-jammy@sha256:e17d77fb030dd4b642dc078d048a5fb9efcb3676ee20305d905949105a6ccd5a

ARG BUILD_REVISION=unknown

LABEL org.opencontainers.image.title="chemicheck119-be" \
      org.opencontainers.image.source="https://github.com/chemicheck119-lab/back" \
      org.opencontainers.image.revision="${BUILD_REVISION}"

RUN groupadd --system --gid 10001 c2guard \
    && useradd --system --uid 10001 --gid c2guard \
        --home-dir /app --shell /usr/sbin/nologin c2guard \
    && mkdir -p /app \
    && chown 10001:10001 /app

WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/build/libs/c2guard.jar /app/app.jar

USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=3 \
    CMD curl --fail --silent --show-error --max-time 2 \
        http://127.0.0.1:8080/actuator/health/liveness >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
