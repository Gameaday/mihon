import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import ephyra.buildlogic.AndroidConfig

plugins {
    id("ephyra.library.multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    androidLibrary {
        namespace = "ephyra.source.api"
        compileSdk = AndroidConfig.COMPILE_SDK
        minSdk = AndroidConfig.MIN_SDK

        optimization {
            consumerKeepRules.file("consumer-proguard.pro")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(kotlinx.serialization.json)
            api(libs.koin.core)
            api(libs.jsoup)

            implementation(project.dependencies.platform(compose.compose.bom))
            implementation(compose.runtime)
        }
        
        androidMain.dependencies {
            implementation(projects.core.common)
            api(libs.preferencektx)

            // Workaround for https://youtrack.jetbrains.com/issue/KT-57605
            implementation(kotlinx.coroutines.android)
            implementation(project.dependencies.platform(kotlinx.coroutines.bom))
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
