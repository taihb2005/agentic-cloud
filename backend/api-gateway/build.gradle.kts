plugins {
    id("java")
    id("vn.edu.hust.kat.spring-boot-conventions")
}

group = "vn.edu.hust.kat.apigateway"
version = "1.0.0"

dependencies {
    // ============================================================
    // Core
    // ===========================================================

    implementation(platform(libs.spring.cloud.dependencies))
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.cloud.starter.gateway.server.webflux)

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
    // Dev Tools
    // ============================================================

    developmentOnly(libs.spring.boot.devtools)

    // ============================================================
    // Lombok
    // ============================================================
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(project(":common"))
}
