# Multi-stage build: compile the fat jar, then ship a slim JRE runtime.
# Run against a target repo by mounting it at /work:
#   docker run --rm -v "$PWD":/work ghcr.io/baekchangjoon/hotspot-analysis:latest \
#     analyze --config /work/hotspot.yml
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew bootJar -q --no-daemon

FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.source="https://github.com/baekchangjoon/hotspot-analysis"
LABEL org.opencontainers.image.description="Rank Java files, methods, and REST API endpoints by a deterministic Composite Hotspot Score to prioritize test generation."
LABEL org.opencontainers.image.licenses="MIT"
WORKDIR /work
COPY --from=build /src/build/libs/hotspot-*.jar /app/hotspot.jar
ENTRYPOINT ["java", "-jar", "/app/hotspot.jar"]
