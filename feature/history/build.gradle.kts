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
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.i18n)
    api(projects.presentationCore)
    // Screen navigation dependencies
    implementation(projects.feature.manga)
    implementation(projects.feature.reader)
    implementation(projects.feature.category)
    implementation(projects.feature.migration)

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

koinCompiler {
    compileSafety.set(true)
    unsafeDslChecks.set(true)
}
