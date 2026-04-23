plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    // Use the native compiler plugin instead of KSP for Koin 4.2 compatibility
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.more"
}

dependencies {
    // Internal project dependencies
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.i18n)
    api(projects.presentationCore)
    api(projects.feature.settings)
    api(projects.feature.download)
    api(projects.feature.category)
    api(projects.feature.stats)
    api(projects.feature.manga)

    // Third-party libraries
    implementation(libs.logcat)
    api(libs.bundles.voyager)
    implementation(libs.bundles.markdown)

    // Dependency Injection (Koin 4.2.0)
    api(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.koin.androidx.compose)

    // Testing
    testImplementation(libs.bundles.test)
}

koinCompiler {
    compileSafety.set(true)
    unsafeDslChecks.set(true)
}
