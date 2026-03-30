plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.reader"

    buildFeatures {
        viewBinding = true
        dataBinding = true // FIX: Needed for ReaderErrorBinding
    }
}

dependencies {
    api(projects.core.common)
    api(projects.core.domain)
    api(projects.core.data)
    api(projects.core.download) // FIX: Needed for download status checks
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.sourceLocal)
    api(projects.i18n)
    api(projects.presentationCore)

    implementation(projects.feature.webview)

    implementation(libs.logcat)
    implementation(compose.activity)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.foundation)
    implementation(compose.animation)
    implementation(compose.ui.util)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    implementation(androidx.lifecycle.runtime.compose)
    implementation(kotlinx.immutables)
    api(libs.bundles.voyager)

    api(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose) // FIX: Resolves koinInject
    implementation(libs.koin.annotations)

    implementation(libs.directionalviewpager) // FIX: Resolves ViewPager errors
    implementation(platform(libs.coil.bom))   // FIX: Resolves Coil errors
    implementation(libs.bundles.coil)

    implementation(libs.subsamplingscaleimageview)
    implementation(libs.image.decoder)
    implementation(libs.unifile)
    implementation(libs.material)

    testImplementation(libs.bundles.test)
}
