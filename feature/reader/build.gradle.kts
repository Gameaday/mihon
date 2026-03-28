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
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.i18n)
    implementation(projects.presentationCore)

    implementation(libs.logcat)
    implementation(libs.bundles.voyager)
    implementation(libs.koin.android)
    
    implementation(libs.subsamplingscaleimageview)
    implementation(libs.image.decoder)
}


