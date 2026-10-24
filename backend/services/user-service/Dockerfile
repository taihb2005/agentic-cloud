# ============================================================================
# user-service  –  multi-stage build  (Gradle 9 / Spring Boot 4.1.1)
# Build context: repository root (backend/)
# ============================================================================

# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# ---------- Gradle wrapper + properties (changes rarely → cached layer) -----
COPY gradlew .
COPY gradle ./gradle
COPY gradle.properties ./

# ---------- Root settings + version catalog --------------------------------
# Gradle 9 requires libs.versions.toml to be present before evaluating
# any build.gradle.kts that references the `libs` accessor.
COPY settings.gradle.kts ./

# ---------- Build-logic (convention plugins) --------------------------------
# build-logic/settings.gradle.kts reads ../gradle/libs.versions.toml,
# so the gradle/ dir (copied above) must already exist.
COPY build-logic ./build-logic

# ---------- All module build scripts (needed by Gradle to configure) --------
COPY common/build.gradle.kts          ./common/
COPY common-proto/build.gradle.kts    ./common-proto/
COPY api-gateway/build.gradle.kts     ./api-gateway/
COPY services/user-service/build.gradle.kts    ./services/user-service/
COPY services/storage-service/build.gradle.kts ./services/storage-service/
COPY services/agent-service/build.gradle.kts   ./services/agent-service/

RUN chmod +x gradlew

# ---------- Dependency resolution (cacheable layer) -------------------------
RUN ./gradlew :services:user-service:dependencies --no-daemon --no-watch-fs

# ---------- Application source code ----------------------------------------
COPY common/src       ./common/src
COPY common-proto/src ./common-proto/src
COPY services/user-service/src ./services/user-service/src

# ---------- Build the fat JAR -----------------------------------------------
RUN ./gradlew :services:user-service:bootJar -x test --no-daemon --no-watch-fs


# ── Stage 2: Runtime ───────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system appgroup \
    && useradd --system --gid appgroup appuser \
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
