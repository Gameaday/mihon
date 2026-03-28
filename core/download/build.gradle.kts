plugins {
    id("ephyra.library")

}

android {
    namespace = "ephyra.core.download"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.sourceApi)
    api(projects.i18n)

    implementation(androidx.workmanager)
    implementation(libs.logcat)
    implementation(libs.unifile)
}


