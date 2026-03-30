import ephyra.buildlogic.getBuildTime
import ephyra.buildlogic.getCommitCount
import ephyra.buildlogic.getGitSha

plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "ephyra.core.data"

    defaultConfig {
        buildConfigField("String", "COMMIT_COUNT", "\"${getCommitCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getGitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = false)}\"")
        buildConfigField("String", "APPLICATION_ID", "\"app.Ephyra\"")
        buildConfigField("String", "VERSION_NAME", "\"0.20.0\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(projects.data)
    api(projects.core.domain)

    implementation(projects.core.common)
    implementation(projects.core.archive)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.i18n)

    implementation(libs.unifile)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.core)
    implementation(libs.okhttp.core)

    implementation(kotlinx.bundles.serialization)
    implementation(kotlinx.immutables)

    api(libs.koin.core)
    api(kotlinx.coroutines.core)
}
// Suppress warnings for the following:

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
}
