import ephyra.buildlogic.AndroidConfig
import ephyra.buildlogic.generatedBuildDir
import ephyra.buildlogic.tasks.getLocalesConfigTask

plugins {
    id("ephyra.library.multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.moko)
}

kotlin {
    android {
        namespace = "ephyra.i18n"
        compileSdk = AndroidConfig.COMPILE_SDK
        minSdk = AndroidConfig.MIN_SDK
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.core)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}


multiplatformResources {
    resourcesPackage.set("ephyra.i18n")
}

val generatedAndroidResourceDir = generatedBuildDir.resolve("android/res")
val localesConfigTask = project.getLocalesConfigTask(generatedAndroidResourceDir)

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(localesConfigTask)
}

