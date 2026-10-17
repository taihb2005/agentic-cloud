plugins {
    id("vn.edu.hust.kat.java-conventions")
    id("vn.edu.hust.kat.spring-boot-conventions")
}

group = "vn.edu.hust.kat.common"
version = "1.0.0"

dependencies {
    implementation("org.springframework:spring-web")

    // ============================================================
    // Lombok
    // ============================================================
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    implementation("com.fasterxml.jackson.core:jackson-annotations:2.22")
}
