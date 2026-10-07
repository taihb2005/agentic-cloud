plugins {
    java
    id("vn.edu.hust.kat.spring-boot-conventions")
}

group = "vn.edu.hust.kat"
version = "0.0.1-SNAPSHOT"
description = "agent-service"

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

