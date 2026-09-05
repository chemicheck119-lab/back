# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk-jammy@sha256:89565961a318534f01c971c7b1d030e60713c66995b887c94010cef938dbc53e AS build

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle

RUN ./gradlew dependencies --no-daemon --console=plain

COPY src ./src

RUN ./gradlew clean bootJar --no-daemon --console=plain

FROM eclipse-temurin:25-jre-jammy@sha256:10c251954d0bfe1a59ba93505f8c628d755919412400aa98685764c9353605d6

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
