plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    // Use the native compiler plugin to solve the Metaspace and KSP errors
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.history"
}

dependencies {
    // Internal project dependencies
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.sourceApi)
    implementation(projects.i18n)
    implementation(projects.presentationCore)

    // Jetpack Compose
    implementation(compose.material3.core)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    // Third-party libraries
    implementation(libs.logcat)
    implementation(libs.bundles.voyager)

    // Dependency Injection (Koin)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)

}
