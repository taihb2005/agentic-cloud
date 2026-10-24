plugins {
    id("vn.edu.hust.kat.java-conventions")
    id("vn.edu.hust.kat.spring-boot-conventions")
}

group = "vn.edu.hust.kat.common"
version = "1.0.0"

dependencies {
    implementation(libs.spring.web)

    // ============================================================
    // Lombok
    // ============================================================
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.jackson.annotations)
}
