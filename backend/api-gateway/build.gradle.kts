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

    implementation("org.springframework.boot:spring-boot-starter-security")

    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux:4.3.5")

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

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

    // ============================================================
    // Dev Tools
    // ============================================================

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ============================================================
    // Lombok
    // ============================================================
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation(project(":common"))
}