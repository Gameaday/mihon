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
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.i18n)
    implementation(projects.presentationCore)

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
    implementation(libs.bundles.voyager)
    implementation(libs.koin.android)

    implementation(libs.subsamplingscaleimageview)
    implementation(libs.image.decoder)
    implementation(libs.unifile)
    implementation(libs.material)
}


