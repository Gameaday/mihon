import ephyra.buildlogic.AndroidConfig

plugins {
    id("ephyra.library.multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

kotlin {
    android {
        namespace = "ephyra.data"
        compileSdk = AndroidConfig.COMPILE_SDK
        minSdk = AndroidConfig.MIN_SDK
        
        // defaultConfig is removed. Proguard rules move to the android block.
        // Note: Use add() for the collection in newer DSL versions
        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }
    }
    
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }

    // Dependencies MUST move into sourceSets for Multiplatform/AGP 9.1
    sourceSets {
        commonMain.dependencies {
            implementation(projects.sourceApi)
            implementation(projects.domain)
            implementation(projects.core.common)

            implementation(libs.room.runtime)
            implementation(libs.room.ktx)
            implementation(libs.room.paging)
        }
    }
}

// KSP must stay top-level but needs the target-aware configuration
dependencies {
    add("kspAndroid", libs.room.compiler)
}
