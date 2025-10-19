# Build-stage
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
WORKDIR /src/file-server
RUN chmod +x gradlew
RUN ./gradlew --no-daemon clean bootJar -x test

# Runtime-stage
FROM eclipse-temurin:17-jre
ENV TZ=Europe/Copenhagen APP_ROOT_FOLDER=/data
WORKDIR /app
COPY --from=build /src/file-server/build/libs/*.jar app.jar
VOLUME ["/data"]
EXPOSE 8085
ENTRYPOINT ["sh","-c","java -jar /app/app.jar"]