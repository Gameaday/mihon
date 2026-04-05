plugins {
    id("ephyra.library")

    kotlin("plugin.serialization")
}

android {
    namespace = "ephyra.core.archive"
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.libarchive)
    implementation(libs.unifile)
}
// Suppress warnings for the following:

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
