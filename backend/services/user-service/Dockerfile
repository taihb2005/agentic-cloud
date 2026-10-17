FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY settings.gradle.kts .
COPY gradle.properties* ./

COPY build-logic ./build-logic
COPY common ./common
COPY services/user-service ./services/user-service

RUN chmod +x gradlew

RUN ./gradlew :services:user-service:bootJar \
    -x test \
    --no-daemon


FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup

USER appuser

COPY --from=builder \
    /app/services/user-service/build/libs/*.jar \
    app.jar


EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]