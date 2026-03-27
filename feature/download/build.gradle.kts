plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("android")
}
android { namespace = "ephyra.feature.download" }
dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    implementation(projects.i18n)
    implementation(libs.bundles.voyager)
    implementation(libs.koin.android)
}
