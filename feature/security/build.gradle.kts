plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("android")
}

android {
    namespace = "ephyra.feature.security"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    implementation(projects.i18n)

    implementation(libs.androidx.biometricktx)
    implementation(libs.koin.android)
}
