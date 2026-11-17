plugins {
    java
    id("vn.edu.hust.kat.spring-boot-conventions")
}

group = "vn.edu.hust.kat.agentservice"
version = "0.0.1-SNAPSHOT"
description = "agent-service"

dependencies {
    // ============================================================
    // Core Spring Boot
    // ============================================================
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.aspects)
    implementation(libs.aspectjweaver)

    // ============================================================
    // Redis (Short-term ChatMemory)
    // ============================================================
    implementation(libs.spring.boot.starter.data.redis)

    // ============================================================
    // Kafka (Data Plane / Event Streaming)
    // ============================================================
    implementation(libs.spring.boot.starter.kafka)

    // ============================================================
    // LangChain4j (AI Orchestration & Cognitive Layer)
    // ============================================================
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.open.ai)
    implementation(libs.langchain4j.pgvector)
    implementation(libs.gson)

    // ============================================================
    // MinIO (Object Storage)
    // ============================================================
    implementation(libs.minio)

    // ============================================================
    // OpenAPI / Swagger
    // ============================================================
    implementation(libs.springdoc.openapi.webmvc)

    // ============================================================
    // Database (PostgreSQL / pgvector / Audit / Flyway)
    // ============================================================
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.h2)
    implementation(libs.hypersistence.utils)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // ============================================================
    // gRPC Client & Server
    // ============================================================
    implementation(libs.protobuf.java)
    implementation(libs.spring.boot.starter.grpc.client)
    implementation(libs.spring.boot.starter.grpc.server)

    // ============================================================
    // Project modules
    // ============================================================
    implementation(project(":common"))
    implementation(project(":common-proto"))

    // ============================================================
    // Lombok
    // ============================================================
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // ============================================================
    // Testing
    // ============================================================
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}
