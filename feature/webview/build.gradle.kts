plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.webview"
}

dependencies {
    // Internal project dependencies
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.i18n)
    api(projects.presentationCore)

    // Third-party libraries
    implementation(libs.logcat)
    implementation(libs.bundles.voyager)

    // Dependency Injection (Koin 4.2.0)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)

    // Testing
    testImplementation(libs.bundles.test)
}
