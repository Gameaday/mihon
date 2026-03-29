plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    id("com.google.devtools.ksp")
}

android {
    namespace = "ephyra.feature.manga"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.core.domain)
    api(projects.data)
    api(projects.core.data)
    api(projects.sourceApi)
    api(projects.sourceLocal)
    api(projects.i18n)
    api(projects.presentationCore)

    implementation(platform(libs.coil.bom))
    implementation(libs.coil.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)

    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    implementation(libs.logcat)
    implementation(libs.bundles.voyager)
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp.compiler)

    testImplementation(libs.bundles.test)
}


