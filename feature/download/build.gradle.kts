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
    // Internal project dependencies
    api(projects.core.common)
    api(projects.domain)
    api(projects.presentationCore)

    // Dependency Injection (Koin)
    api(libs.koin.core)
    implementation(libs.koin.annotations)
}
