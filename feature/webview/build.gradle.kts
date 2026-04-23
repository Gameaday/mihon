plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.webview"
    buildFeatures {
        viewBinding = true
        compose = true
    }
}

dependencies {
    // Internal project dependencies
    api(projects.core.common)
    api(projects.core.domain)
    api(projects.core.data)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.sourceLocal)
    api(projects.i18n)
    api(projects.presentationCore)

    // FIX: All Compose UI libraries restored so @Composable works again!
    implementation(compose.activity)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.foundation)
    implementation(compose.animation)
    implementation(compose.ui.util)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    // Third-party & Navigation
    implementation(libs.logcat)
    implementation(libs.compose.webview)
    implementation(androidx.appcompat)
    api(libs.bundles.voyager)

    // Dependency Injection
    api(libs.koin.core)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.annotations)

    testImplementation(libs.bundles.test)
}

koinCompiler {
    compileSafety.set(true)
    unsafeDslChecks.set(true)
}
