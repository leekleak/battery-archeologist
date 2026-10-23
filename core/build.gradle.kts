plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

kotlin {
    jvmToolchain(21)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.3"
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.protobuf.java)

    testImplementation(libs.junit)
}
