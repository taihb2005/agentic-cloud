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

    implementation(libs.spring.boot.starter.web)

    implementation(libs.spring.boot.starter.data.jpa)

    implementation(libs.spring.boot.starter.security)

    implementation(libs.spring.boot.starter.validation)

    implementation(libs.spring.boot.starter.actuator)

    implementation(libs.spring.boot.starter.flyway)

    // ============================================================
    // KAFKA
    // ============================================================

    implementation(libs.spring.boot.starter.kafka)

    // ============================================================
    // JWT
    // ============================================================

    implementation(libs.jjwt.api)

    runtimeOnly(libs.jjwt.impl)

    runtimeOnly(libs.jjwt.jackson)


    // ============================================================
    // Rate Limiting
    // ============================================================

    implementation(libs.bucket4j.core)


    // ============================================================
    // OpenAPI / Swagger
    // ============================================================

    implementation(libs.springdoc.openapi.webmvc)


    // ============================================================
    // Database
    // ============================================================

    runtimeOnly(libs.postgresql)

    runtimeOnly(libs.h2)


    // ============================================================
    // Flyway
    // ============================================================

    implementation(libs.flyway.core)

    implementation(libs.flyway.postgresql)

    // ============================================================
    // Dev Tools
    // ============================================================

    developmentOnly(libs.spring.boot.devtools)

    // ============================================================
    // Lombok
    // ============================================================
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // ============================================================
    // gRPC
    // ============================================================
    implementation(libs.protobuf.java)
    implementation(libs.spring.boot.starter.grpc.server)
    implementation(libs.spring.boot.starter.grpc.client)

    implementation(project(":common"))
    implementation(project(":common-proto"))
}
