plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    // Use the native compiler plugin to resolve the KSP errors
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.download"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.presentationCore)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
}
