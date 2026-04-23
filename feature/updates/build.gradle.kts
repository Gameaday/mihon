plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.updates"
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
    implementation(projects.feature.reader)
    implementation(projects.feature.download)
    implementation(projects.feature.upcoming)

    // Third-party libraries
    implementation(libs.logcat)
    api(libs.bundles.voyager)

    // Dependency Injection (Koin 4.2.0)
    api(libs.koin.core)
    implementation(libs.koin.annotations)

    // Testing
    testImplementation(libs.bundles.test)
}

koinCompiler {
    compileSafety.set(true)
    unsafeDslChecks.set(true)
}
