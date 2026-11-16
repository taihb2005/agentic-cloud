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

    // ============================================================
    // MinIO (Object Storage)
    // ============================================================
    implementation(libs.minio)

    // ============================================================
    // OpenAPI / Swagger
    // ============================================================
    implementation(libs.springdoc.openapi.webmvc)

    // ============================================================
    // Database (PostgreSQL / pgvector / Audit)
    // ============================================================
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.h2)
    implementation(libs.hypersistence.utils)

    // ============================================================
    // gRPC Client & Inter-service Communication
    // ============================================================
    implementation(libs.protobuf.java)
    implementation(libs.spring.boot.starter.grpc.client)

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

    implementation(libs.protobuf.java)
    implementation(libs.spring.boot.starter.grpc.server)

    implementation(project(":common"))
    implementation(project(":common-proto"))
}
