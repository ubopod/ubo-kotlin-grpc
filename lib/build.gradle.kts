plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ubopod.ubokotlin"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // The proto messages + grpc-Java + grpc-Kotlin stubs live in the sibling
    // `:protos` module. Compiling there means the Kotlin compiler in this
    // module only sees the small compiled .class bytecode, not the 20 MB
    // generated Ubo.java source — which the Kotlin compiler's symbol
    // indexer can't ingest within reasonable time/memory.
    api(project(":protos"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
