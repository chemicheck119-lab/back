# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy@sha256:723151f3fc88ca2060153ee08ab8dbbea7983d6ed6f2622fe440acf178737c94 AS build

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle

RUN ./gradlew dependencies --no-daemon --console=plain

COPY src ./src

RUN ./gradlew clean bootJar --no-daemon --console=plain

FROM eclipse-temurin:17-jre-jammy@sha256:475d8e96b4b2bfe08999e5e854755c773af1581acdf959a4545d88f0696a2339

ARG BUILD_REVISION=unknown

LABEL org.opencontainers.image.title="chemicheck119-be" \
      org.opencontainers.image.source="https://github.com/chemicheck119/BE_Repository" \
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
