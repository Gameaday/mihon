plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

}

android {
    namespace = "ephyra.feature.settings"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.i18n)
    api(projects.presentationCore)

    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.foundation)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    api(libs.bundles.voyager)
    api(libs.koin.core)
    implementation(libs.koin.android)
}
