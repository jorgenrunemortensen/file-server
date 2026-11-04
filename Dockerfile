# ── Build stage ─────────────────────────────────────────────────────────────
# The JAR is platform independent, so it is always built on the native platform of the build machine.
# Build with:  docker build --build-context common-src=../common .
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build

# Mirror the host folder layout, so includeBuild('../common') resolves
COPY --from=common-src . /workspace/common/
WORKDIR /workspace/file-server

# Copy Gradle wrapper and project descriptor files first (better layer caching)
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Copy source and build the fat JAR
COPY src/ src/
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# ── Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
ENV TZ=Europe/Copenhagen APP_ROOT_FOLDER=/data
WORKDIR /app
COPY --from=build /workspace/file-server/build/libs/*.jar app.jar
VOLUME ["/data"]
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
