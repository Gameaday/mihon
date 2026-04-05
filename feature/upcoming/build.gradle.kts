plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ephyra.feature.upcoming"
}

dependencies {
    // Internal project dependencies
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.i18n)
    api(projects.presentationCore)
    implementation(projects.feature.manga)

    // Jetpack Compose
    implementation(compose.material3.core)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    // Third-party libraries
    implementation(libs.logcat)
    api(libs.bundles.voyager)

    // Dependency Injection (Koin)
    api(libs.koin.core)
    implementation(libs.koin.annotations)
}
