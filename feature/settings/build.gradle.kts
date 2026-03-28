plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

}

android {
    namespace = "ephyra.feature.settings"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.i18n)
    implementation(projects.presentationCore)

    implementation(libs.bundles.voyager)
    implementation(libs.koin.android)
}


