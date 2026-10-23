plugins {
    java
    id("vn.edu.hust.kat.spring-boot-conventions")
}

group = "vn.edu.hust.kat.userservice"
version = "0.0.1-SNAPSHOT"
description = "user-service"

dependencies {
    // ============================================================
    // Core
    // ============================================================

    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("org.springframework.boot:spring-boot-starter-security")

    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // ============================================================
    // KAFKA
    // ============================================================

    implementation("org.springframework.kafka:spring-kafka:3.3.16")

    // ============================================================
    // JWT
    // ============================================================

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")

    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")

    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")


    // ============================================================
    // Rate Limiting
    // ============================================================

    implementation("com.bucket4j:bucket4j-core:8.10.1")


    // ============================================================
    // OpenAPI / Swagger
    // ============================================================

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")


    // ============================================================
    // Database
    // ============================================================

    runtimeOnly("org.postgresql:postgresql")

    runtimeOnly("com.h2database:h2")


    // ============================================================
    // Flyway
    // ============================================================

    implementation("org.flywaydb:flyway-core")

    implementation("org.flywaydb:flyway-database-postgresql")

    // ============================================================
    // Dev Tools
    // ============================================================

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ============================================================
    // Lombok
    // ============================================================
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ============================================================
    // gRPC
    // ============================================================
    implementation("org.springframework.boot:spring-boot-starter-grpc-server:4.1.0")
    implementation("org.springframework.boot:spring-boot-starter-grpc-client:4.1.0")

    implementation(project(":common"))
    implementation(project(":common-proto"))
}
