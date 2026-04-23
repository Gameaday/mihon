plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.browse"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.i18n)
    api(projects.presentationCore)
    api(projects.feature.manga)
    api(projects.feature.webview)
    api(projects.feature.category)
    api(projects.feature.migration)

    implementation(libs.logcat)
    api(libs.bundles.voyager)
    api(libs.koin.core)
    implementation(libs.koin.annotations)

    testImplementation(libs.bundles.test)
}

koinCompiler {
    compileSafety.set(true)
    unsafeDslChecks.set(true)
}
