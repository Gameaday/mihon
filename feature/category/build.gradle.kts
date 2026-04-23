plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.category"
}

dependencies {
    // Internal project dependencies
    api(projects.core.common)
    api(projects.domain)
    api(projects.core.domain)
    api(projects.data)
    api(projects.core.data)
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
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.annotations)

    testImplementation(libs.bundles.test)
}

koinCompiler {
    compileSafety.set(true)
    unsafeDslChecks.set(true)
}
