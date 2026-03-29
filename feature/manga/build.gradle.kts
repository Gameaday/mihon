plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    // FIX: Use the native compiler plugin instead of KSP for Koin 4.2 compatibility
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.manga"
}

dependencies {
    // Internal project dependencies
    api(projects.core.common)
    api(projects.domain)
    api(projects.core.domain)
    api(projects.data)
    api(projects.core.data)
    api(projects.sourceApi)
    api(projects.sourceLocal)
    api(projects.i18n)
    api(projects.presentationCore)

    // Image loading (Coil 3)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)

    // Jetpack Compose
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    // Third-party libraries
    implementation(libs.logcat)
    api(libs.bundles.voyager)

    // Dependency Injection (Koin 4.2.0)
    api(libs.koin.core)
    implementation(libs.koin.annotations)

    // Testing
    testImplementation(libs.bundles.test)
}
