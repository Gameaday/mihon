plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

}

android {
    namespace = "ephyra.feature.reader"

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    api(projects.core.common)
    api(projects.core.domain)
    api(projects.core.data)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.sourceLocal)
    api(projects.i18n)
    api(projects.presentationCore)

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

    implementation(libs.subsamplingscaleimageview)
    implementation(libs.image.decoder)
    implementation(libs.unifile)
    implementation(libs.material)
}
