plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ephyra.data"
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
}
