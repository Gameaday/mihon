plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
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
}
