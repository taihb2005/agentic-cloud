plugins {
    id("java")
    id("java-gradle-plugin")
}

group = "vn.edu.hust.kat"
version = "1.0-SNAPSHOT"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:${libs.versions.spring.boot.get()}")
    implementation("io.spring.gradle:dependency-management-plugin:${libs.versions.spring.dependency.management.get()}")
    implementation("com.google.protobuf:protobuf-gradle-plugin:${libs.versions.protobuf.plugin.get()}")
}

gradlePlugin {
    plugins {
        create("javaConventions") {
            id = "vn.edu.hust.kat.java-conventions"
            implementationClass =
                "vn.edu.hust.kat.buildlogic.JavaConventionsPlugin"
        }

        create("springBootConventions") {
            id = "vn.edu.hust.kat.spring-boot-conventions"
            implementationClass =
                "vn.edu.hust.kat.buildlogic.SpringBootConventionsPlugin"
        }

        create("libraryConventions") {
            id = "vn.edu.hust.kat.library-conventions"
            implementationClass =
                "vn.edu.hust.kat.buildlogic.LibraryConventionsPlugin"
        }

        create("protobufConventions") {
            id = "vn.edu.hust.kat.protobuf-conventions"
            implementationClass =
                "vn.edu.hust.kat.buildlogic.ProtobufConventionsPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
