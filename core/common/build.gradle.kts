plugins {
    id("ephyra.library")

    kotlin("plugin.serialization")
}

android {
    namespace = "ephyra.app.core.common"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.DelicateCoroutinesApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(projects.i18n)

    api(libs.koin.core)
    api(libs.logcat)
    implementation(libs.material)

    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsoverhttps)
    api(libs.okio)

    implementation(libs.image.decoder)

    implementation(libs.unifile)
    implementation(libs.libarchive)

    api(platform(kotlinx.coroutines.bom))
    api(kotlinx.coroutines.core)
    api(kotlinx.serialization.json)
    api(kotlinx.serialization.json.okio)

    api(libs.preferencektx)
    api(libs.datastore)

    api(androidx.workmanager)

    api(libs.bundles.shizuku)

    implementation(libs.jsoup)

    // JavaScript engine
    implementation(libs.bundles.js.engine)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
// Suppress warnings for the following:

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
}
