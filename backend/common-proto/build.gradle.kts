plugins {
    id("vn.edu.hust.kat.protobuf-conventions")
}

group = "vn.edu.hust.kat.proto"
version = "1.0.0"

sourceSets {
    main {
        proto {
            srcDir("src/main/protobuf")
            srcDir("src/main/java/vn/edu/hust/kat/proto")
        }
    }
}
