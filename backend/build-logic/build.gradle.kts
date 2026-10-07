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
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.7")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
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
    }
}

tasks.test {
    useJUnitPlatform()
}