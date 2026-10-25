FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY settings.gradle.kts .
COPY gradle.properties* ./

COPY build-logic ./build-logic
COPY common ./common

# ---------- All module build scripts (needed by Gradle to configure) --------
COPY common/build.gradle.kts          ./common/
COPY common-proto/build.gradle.kts    ./common-proto/
COPY api-gateway/build.gradle.kts     ./api-gateway/
COPY services/user-service/build.gradle.kts    ./services/user-service/
COPY services/storage-service/build.gradle.kts ./services/storage-service/
COPY services/agent-service/build.gradle.kts   ./services/agent-service/

RUN chmod +x gradlew

# ---------- Dependency resolution (cacheable layer) -------------------------
RUN ./gradlew :api-gateway:dependencies --no-daemon --no-watch-fs

# ---------- Application source code ----------------------------------------
COPY common/src       ./common/src
COPY common-proto/src ./common-proto/src
COPY api-gateway/src ./api-gateway/src

# ---------- Build the fat JAR -----------------------------------------------
RUN ./gradlew :api-gateway:bootJar -x test --no-daemon --no-watch-fs

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system appgroup \
    && useradd --system --gid appgroup appuser \
    && mkdir -p /var/log/api-gateway \
    && touch /var/log/api-gateway/app.log \
    && chown -R appuser:appgroup /var/log/api-gateway \
    && chmod 664 /var/log/api-gateway/app.log

COPY --from=builder \
    /app/api-gateway/build/libs/*.jar \
    ./app.jar

RUN chown appuser:appgroup /app/app.jar

USER appuser


EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]