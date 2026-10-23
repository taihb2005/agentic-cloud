FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY settings.gradle.kts ./
COPY gradle.properties ./

COPY build-logic ./build-logic

COPY common/build.gradle.kts ./common/
COPY services/user-service/build.gradle.kts ./services/user-service/

RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :services:user-service:dependencies --no-daemon

COPY common/src ./common/src
COPY services/user-service/src ./services/user-service/src

RUN ./gradlew :services:user-service:bootJar -x test --no-daemon


FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup \
    && adduser -S appuser -G appgroup \
    && mkdir -p /var/log/user-service \
    && touch /var/log/user-service/app.log \
    && chown -R appuser:appgroup /var/log/user-service \
    && chmod 664 /var/log/user-service/app.log

COPY --from=builder \
    /app/services/user-service/build/libs/*.jar \
    ./app.jar

RUN chown appuser:appgroup /app/app.jar

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]