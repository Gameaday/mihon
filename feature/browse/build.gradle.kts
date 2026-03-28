plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    id("com.google.devtools.ksp")
}

android {
    namespace = "ephyra.feature.browse"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.i18n)
    api(projects.presentationCore)
    api(projects.feature.manga)

    implementation(libs.logcat)
    implementation(libs.bundles.voyager)
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp.compiler)

    testImplementation(libs.bundles.test)
}


