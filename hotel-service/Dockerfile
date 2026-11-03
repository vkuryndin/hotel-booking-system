# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY config ./config
COPY discovery-server/build.gradle.kts discovery-server/build.gradle.kts
COPY api-gateway/build.gradle.kts api-gateway/build.gradle.kts
COPY hotel-service/build.gradle.kts hotel-service/build.gradle.kts
COPY booking-service/build.gradle.kts booking-service/build.gradle.kts
COPY hotel-service/src/main hotel-service/src/main
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon --console=plain --max-workers=2 \
    -Dorg.gradle.jvmargs='-Xmx768m -XX:MaxMetaspaceSize=384m' \
    :hotel-service:bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/hotel-service/build/libs/*.jar /app/app.jar
RUN mkdir -p /app/storage/hotels /app/storage/rooms && \
    chown -R 10001:10001 /app
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
