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
COPY discovery-server/src/main discovery-server/src/main
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon --console=plain --max-workers=2 \
    -Dorg.gradle.jvmargs='-Xmx768m -XX:MaxMetaspaceSize=384m' \
    :discovery-server:bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/discovery-server/build/libs/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
