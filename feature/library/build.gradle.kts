plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.library"
}

dependencies {
    implementation(projects.presentationCore)
    implementation(projects.feature.manga)
    implementation(projects.feature.reader)
    implementation(projects.feature.category)
    implementation(projects.feature.browse)
    implementation(projects.feature.download)
    // Internal project dependencies
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.i18n)
    api(projects.presentationCore)

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
