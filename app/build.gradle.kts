import ephyra.buildlogic.Config
import ephyra.buildlogic.getBuildTime
import ephyra.buildlogic.getCommitCount
import ephyra.buildlogic.getGitSha
import ephyra.buildlogic.tasks.LocalesConfigTask

plugins {
    id("ephyra.android.application")
    id("ephyra.android.application.compose")
    kotlin("plugin.serialization")
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.aboutLibraries)
}

if (Config.includeTelemetry) {
    pluginManager.apply {
        apply(libs.plugins.google.services.get().pluginId)
        apply(libs.plugins.firebase.crashlytics.get().pluginId)
    }
}

android {
    namespace = "ephyra.app"

    // NDK r29+ is required for ARMv9.2-A (arm64-v8a with SVE2/SME) support used by
    // devices such as the Samsung Galaxy S24 series (Snapdragon 8 Gen 3 / Exynos 2400).
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "app.Ephyra"

        versionCode = 20
        versionName = "0.20.0"

        buildConfigField("String", "COMMIT_COUNT", "\"${getCommitCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getGitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = false)}\"")
        buildConfigField("boolean", "TELEMETRY_INCLUDED", "${Config.includeTelemetry}")
        buildConfigField("boolean", "UPDATER_ENABLED", "${Config.enableUpdater}")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // arm64-v8a covers ARMv8 through ARMv9.2-A (e.g. Samsung Galaxy S24 series).
            // The universal APK and the dedicated arm64-v8a split both target this ABI.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildTypes {
        val debug by getting {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-${getCommitCount()}"
            isPseudoLocalesEnabled = true
        }
        val release by getting {
            isMinifyEnabled = Config.enableCodeShrink
            isShrinkResources = Config.enableCodeShrink

            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")

            buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = true)}\"")
        }

        val commonMatchingFallbacks = listOf(release.name)

        create("nightly") {
            initWith(release)

            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-nightly-${getGitSha()}"

            matchingFallbacks.addAll(commonMatchingFallbacks)
        }
        create("foss") {
            initWith(release)

            applicationIdSuffix = ".foss"

            matchingFallbacks.addAll(commonMatchingFallbacks)
        }
        create("preview") {
            initWith(release)

            applicationIdSuffix = ".debug"

            versionNameSuffix = debug.versionNameSuffix
            signingConfig = debug.signingConfig

            matchingFallbacks.addAll(commonMatchingFallbacks)

            buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = false)}\"")
        }
        create("benchmark") {
            initWith(release)

            isDebuggable = false
            isProfileable = true
            versionNameSuffix = "-benchmark"
            applicationIdSuffix = ".benchmark"

            signingConfig = debug.signingConfig

            matchingFallbacks.addAll(commonMatchingFallbacks)
        }
    }

    sourceSets {
        getByName("preview").res.directories.add(file("src/debug/res").toString())
        getByName("benchmark").res.directories.add(file("src/debug/res").toString())
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols += listOf(
                "libandroidx.graphics.path",
                "libarchive-jni",
                "libimagedecoder",
                "libquickjs",
                "libsqlite3x",
            )
                .map { "**/$it.so" }
        }
        resources {
            excludes += setOf(
                "kotlin-tooling-metadata.json",
                "LICENSE.txt",
                "META-INF/**/*.properties",
                "META-INF/**/LICENSE.txt",
                "META-INF/*.properties",
                "META-INF/*.version",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/README.md",
            )
        }
    }

    dependenciesInfo {
        includeInApk = Config.includeDependencyInfo
        includeInBundle = Config.includeDependencyInfo
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true

        // Disable some unused things
        dataBinding = false
        shaders = false
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
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
            "-opt-in=kotlinx.coroutines.DelicateCoroutinesApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-Xannotation-default-target=param-property",
        )
    }
}

dependencies {
    implementation(projects.i18n)
    implementation(projects.core.archive)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.download)
    implementation(projects.coreMetadata)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.data)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    implementation(projects.presentationWidget)

    // Feature modules
    implementation(projects.feature.browse)
    implementation(projects.feature.category)
    implementation(projects.feature.download)
    implementation(projects.feature.history)
    implementation(projects.feature.library)
    implementation(projects.feature.manga)
    implementation(projects.feature.migration)
    implementation(projects.feature.more)
    implementation(projects.feature.reader)
    implementation(projects.feature.security)
    implementation(projects.feature.settings)
    implementation(projects.feature.stats)
    implementation(projects.feature.upcoming)
    implementation(projects.feature.updates)
    implementation(projects.feature.webview)
    implementation(projects.telemetry)

    // Compose
    implementation(compose.activity)
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.animation)
    implementation(compose.animation.graphics)
    debugImplementation(compose.ui.tooling)
    implementation(compose.ui.tooling.preview)
    implementation(compose.ui.util)

    implementation(androidx.paging.runtime)
    implementation(androidx.paging.compose)

    implementation(libs.bundles.sqlite)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    implementation(kotlinx.reflect)
    implementation(kotlinx.immutables)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)

    // AndroidX libraries
    implementation(androidx.annotation)
    implementation(androidx.appcompat)
    implementation(androidx.biometricktx)
    implementation(androidx.corektx)
    implementation(androidx.splashscreen)
    implementation(androidx.recyclerview)
    implementation(androidx.viewpager)
    implementation(androidx.profileinstaller)

    implementation(androidx.bundles.lifecycle)

    // Job scheduling
    implementation(androidx.workmanager)

    // Networking
    implementation(libs.bundles.okhttp)
    implementation(libs.okio)

    // Data serialization (JSON, protobuf, xml)
    implementation(kotlinx.bundles.serialization)

    // HTML parser
    implementation(libs.jsoup)

    // Disk
    implementation(libs.unifile)

    // Preferences
    implementation(libs.preferencektx)

    // Dependency injection
    implementation(libs.bundles.koin)

    // Image loading
    implementation(platform(libs.coil.bom))
    implementation(libs.bundles.coil)
    implementation(libs.subsamplingscaleimageview) {
        exclude(module = "image-decoder")
    }
    implementation(libs.image.decoder)

    // UI libraries
    implementation(libs.material)
    implementation(libs.directionalviewpager) {
        exclude(group = "androidx.viewpager", module = "viewpager")
    }

    implementation(libs.aboutLibraries.compose)
    implementation(libs.bundles.voyager)
    implementation(libs.compose.materialmotion)
    implementation(libs.swipe)
    implementation(libs.compose.webview)
    implementation(libs.compose.grid)
    implementation(libs.reorderable)
    implementation(libs.bundles.markdown)
    implementation(libs.materialKolor)

    // Logging
    implementation(libs.logcat)

    // Shizuku
    implementation(libs.bundles.shizuku)

    // String similarity
    implementation(libs.stringSimilarity)

    // Tests
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(kotlinx.coroutines.test)

    // Koin test utilities – static module graph verification and isolated Koin contexts
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
}

val generateLocalesConfig = tasks.register<LocalesConfigTask>("generateLocalesConfig") {
    mokoResourcesDir.set(project(":i18n").file("src/commonMain/moko-resources/"))
    outputDir.set(layout.buildDirectory.dir("generated/res/locales_config"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generateLocalesConfig, LocalesConfigTask::outputDir)
    }
    onVariants(selector().withFlavor("default" to "standard")) {
        // Only excluding in standard flavor because this breaks
        // Layout Inspector's Compose tree
        it.packaging.resources.excludes.add("META-INF/*.version")
    }
    onVariants(selector().withBuildType("nightly")) { variant ->
        // Use the versionCode Gradle property (set to GITHUB_RUN_NUMBER in CI) so that
        // each nightly build gets a unique, monotonically increasing version code that
        // allows in-place upgrades between successive nightly releases.
        val nightlyVersionCodeStr = project.findProperty("versionCode") as String?
        if (nightlyVersionCodeStr != null) {
            val nightlyVersionCode = nightlyVersionCodeStr.toIntOrNull()
                ?: error("Gradle property 'versionCode' must be a valid integer, got: '$nightlyVersionCodeStr'")
            variant.outputs.forEach { output ->
                output.versionCode.set(nightlyVersionCode)
            }
        }
    }
}

buildscript {
    dependencies {
        classpath(kotlinx.gradle)
    }
}

koinCompiler {
    compileSafety.set(false)
}
