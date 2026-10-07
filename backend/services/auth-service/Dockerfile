FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY settings.gradle.kts .
COPY gradle.properties* ./

COPY build-logic ./build-logic
COPY services/auth-service ./services/auth-service

RUN chmod +x gradlew

RUN ./gradlew :services:auth-service:bootJar \
    -x test \
    --no-daemon


FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup

USER appuser

COPY --from=builder \
    /app/services/auth-service/build/libs/*.jar \
    app.jar


EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]