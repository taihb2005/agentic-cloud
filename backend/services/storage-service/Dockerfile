FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY settings.gradle.kts ./
COPY gradle.properties ./

COPY build-logic ./build-logic

COPY common/build.gradle.kts ./common/
COPY services/storage-service/build.gradle.kts ./services/storage-service/

RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :services:storage-service:dependencies --no-daemon

COPY common/src ./common/src
COPY services/storage-service/src ./services/storage-service/src

RUN ./gradlew :services:storage-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup \
    && adduser -S appuser -G appgroup \
    && mkdir -p /var/log/storage-service \
    && touch /var/log/storage-service/app.log \
    && chown -R appuser:appgroup /var/log/storage-service \
    && chmod 664 /var/log/storage-service/app.log

COPY --from=builder \
    /app/services/storage-service/build/libs/*.jar \
    ./app.jar

RUN chown appuser:appgroup /app/app.jar

USER appuser

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
