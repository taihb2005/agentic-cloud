rootProject.name = "agentic-cloud-platform"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include(
    ":services:user-service",
    ":services:agent-service",
    ":services:storage-service",
    ":common",
    ":api-gateway",
    ":common-proto"
)