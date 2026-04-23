plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    id("org.jetbrains.kotlin.plugin.compose")
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

    // 1. SIBLING FEATURES (MangaScreen launches these!)
    implementation(projects.feature.reader)
    implementation(projects.feature.webview)
    implementation(projects.feature.category)
    implementation(projects.feature.migration)

    // 2. MISSING UI LIBRARIES (Markdown, Swipe, etc.)
    implementation(libs.bundles.markdown)
    implementation(libs.swipe)
    implementation(libs.compose.materialmotion)

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
    implementation(libs.koin.androidx.compose)

    // Testing
    testImplementation(libs.bundles.test)
}

koinCompiler {
    compileSafety.set(true)
    unsafeDslChecks.set(true)
}
