plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("android")
}

android {
    namespace = "ephyra.feature.more"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    implementation(projects.i18n)
    
    implementation(projects.feature.settings)
    implementation(projects.feature.category)
    implementation(projects.feature.download)
    implementation(projects.feature.security)
    implementation(projects.feature.stats)

    implementation(libs.bundles.voyager)
    implementation(libs.koin.android)
    implementation(libs.bundles.coil)
}
