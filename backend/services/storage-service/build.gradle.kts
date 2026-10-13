plugins {
    java
    id("vn.edu.hust.kat.spring-boot-conventions")
}

group = "vn.edu.hust.kat"
version = "0.0.1-SNAPSHOT"
description = "auth-service"

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
    // Database
    // ============================================================

    runtimeOnly("com.mysql:mysql-connector-j")

    runtimeOnly("com.h2database:h2")


    // ============================================================
    // Flyway
    // ============================================================

    implementation("org.flywaydb:flyway-core")

    implementation("org.flywaydb:flyway-mysql")


    // ============================================================
    // Spring Session JDBC
    // ============================================================

    implementation("org.springframework.session:spring-session-jdbc")


    // ============================================================
    // Dev Tools
    // ============================================================

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ============================================================
    // Lombok
    // ============================================================
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}