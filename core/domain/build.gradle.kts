plugins {
    id("ephyra.library")
}

android {
    namespace = "ephyra.core.domain"
}

dependencies {
    api(projects.domain)
    implementation(projects.core.common)
    implementation(projects.sourceApi)
    implementation(projects.data)
    implementation(androidx.workmanager)
    implementation(libs.sqldelight.coroutines)

    api(libs.koin.core)
    api(kotlinx.coroutines.core)
}
