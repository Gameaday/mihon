plugins {
    id("ephyra.library")

    kotlin("plugin.serialization")
}

android {
    namespace = "ephyra.core.archive"
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.libarchive)
    implementation(libs.unifile)
}


