plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "eu.kanade.tachiyomi.core.common"
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

    api(libs.logcat)

    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsoverhttps)
    api(libs.okio)

    implementation(libs.image.decoder)

    implementation(libs.unifile)
    implementation(libs.libarchive)

    api(kotlinx.coroutines.core)
    api(kotlinx.serialization.json)
    api(kotlinx.serialization.json.okio)

    api(libs.preferencektx)

    implementation(libs.jsoup)

    // JavaScript engine
    implementation(libs.bundles.js.engine)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
