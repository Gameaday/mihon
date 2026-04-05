plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
    // Keep KSP for Room database processing
    id("com.google.devtools.ksp")
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "ephyra.data"
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("ephyra.data")
        }
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)

    // Room - Processed via KSP
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // SQLDelight
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)

    // Koin - Handled by the Compiler Plugin (no ksp line needed)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
}
