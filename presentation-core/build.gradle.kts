plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
}

android {
    namespace = "ephyra.presentation.core"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    // Internal Layers
    api(projects.core.common)
    api(projects.domain)
    api(projects.core.domain)
    api(projects.core.data)
    api(projects.core.archive)
    api(projects.core.download)
    api(projects.sourceLocal)
    api(projects.sourceApi)
    api(projects.i18n)

    // Compose Core
    api(compose.activity)
    api(compose.foundation)
    api(compose.material3.core)
    api(compose.material.icons)
    api(compose.animation)
    api(compose.animation.graphics)
    debugImplementation(compose.ui.tooling)
    api(compose.ui.tooling.preview)
    api(compose.ui.util)

    // Essential UI/Lifecycle Libraries
    api(androidx.paging.runtime)
    api(androidx.paging.compose)
    api(androidx.appcompat)
    api(androidx.lifecycle.runtimektx)
    api(kotlinx.immutables)
    api(androidx.lifecycle.runtime.compose)
    api(libs.image.decoder)
    api(libs.materialKolor)
    api(libs.material)
    api(libs.compose.materialmotion)
    api(libs.reorderable)

    // Image Loading & Files
    api(platform(libs.coil.bom))
    api(libs.bundles.coil)
    api(libs.unifile)
    api(libs.okio)

    // Navigation & DI
    api(libs.bundles.voyager)
    api(libs.koin.android)
    api(libs.koin.androidx.compose)
    api(libs.koin.annotations) // Added this so features can use DI annotations

    // Utilities
    api(libs.shizuku.api)
    api(androidx.biometricktx)
}
