import com.google.protobuf.gradle.id

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

protobuf {
    protoc {
        artifact = libs.protoc.binary.get().toString()
    }
    plugins {
        id("grpc") {
            artifact = libs.protoc.gen.grpc.java.get().toString()
        }
        id("grpckt") {
            artifact = "${libs.protoc.gen.grpc.kotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        // Plain Java is the default builtin on a `java-library` project, so
        // we don't add `id("java")` explicitly (that would double-register).
        // Keeping this module pure-Java for messages means downstream Kotlin
        // consumers see compiled .class bytecode (small, indexable) instead
        // of the 20 MB Ubo.java source — which the Kotlin compiler's symbol
        // indexer cannot ingest within reasonable memory.
        // The `grpckt` plugin emits coroutine stubs as Kotlin source, which
        // we DO compile here (kotlin-jvm plugin) so consumers see them as
        // bytecode too.
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
        }
    }
}

// Wire the generated Kotlin grpc stubs into the Kotlin source set so the
// kotlin-jvm plugin compiles them. The grpckt plugin writes to
// build/generated/source/proto/main/grpckt/.
sourceSets.named("main") {
    java.srcDirs(
        layout.buildDirectory.dir("generated/source/proto/main/grpckt"),
    )
}

dependencies {
    api(libs.protobuf.java)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.kotlin.stub)
    api(libs.grpc.okhttp)
    api(libs.coroutines.core)
}
