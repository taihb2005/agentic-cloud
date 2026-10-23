import com.google.protobuf.gradle.id

plugins {
    id("java")
    id("vn.edu.hust.kat.java-conventions")
    id("com.google.protobuf") version "0.10.0"
}

group = "vn.edu.hust.kat.proto"
version = "1.0.0"

dependencies {
    implementation("io.grpc:grpc-protobuf:1.85.1")
    implementation("io.grpc:grpc-stub:1.85.1")
    implementation("com.google.protobuf:protobuf-java:4.36.2")

    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
    compileOnly("jakarta.annotation:jakarta.annotation-api:3.0.0")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.36.2"
    }

    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.85.1"
        }
    }

    generateProtoTasks {
        all().configureEach {
            plugins {
                id("grpc") {
                    option("@generated=omit")
                    option("jakarta_omit")
                }
            }
        }
    }
}


sourceSets {
    main {
        proto {
            srcDir ("src/main/protobuf")
            srcDir("src/main/java/vn/edu/hust/kat/proto")
        }
    }
}