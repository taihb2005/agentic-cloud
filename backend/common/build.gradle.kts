plugins {
    id("vn.edu.hust.kat.java-conventions")
    id("vn.edu.hust.kat.spring-boot-conventions")
}

group = "vn.edu.hust.kat.common"
version = "1.0.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("org.springframework.boot:spring-boot-starter-security")

    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // ============================================================
    // Lombok
    // ============================================================
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    implementation("com.fasterxml.jackson.core:jackson-annotations:2.22")
}
