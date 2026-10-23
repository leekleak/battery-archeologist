plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.wire)
}

kotlin {
    jvmToolchain(21)
}

wire {
    kotlin {
        // The parser is a port of builder-based Go code; keep Wire's builder API so the
        // incremental construction reads the same way.
        javaInterop = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.wire.runtime)

    testImplementation(libs.junit)
}
