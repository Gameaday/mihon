package ephyra.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

val Project.androidxCatalog get() = extensions.getByType<VersionCatalogsExtension>().named("androidx")
val Project.composeCatalog get() = extensions.getByType<VersionCatalogsExtension>().named("compose")
val Project.kotlinxCatalog get() = extensions.getByType<VersionCatalogsExtension>().named("kotlinx")
val Project.libsCatalog get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun VersionCatalog.getLib(name: String) =
    findLibrary(name).orElseThrow { NoSuchElementException("Library $name not found in catalog") }.get()

private fun VersionCatalog.getPlugin(name: String) =
    findPlugin(name).orElseThrow { NoSuchElementException("Plugin $name not found in catalog") }.get()

internal fun Project.configureAndroid(commonExtension: CommonExtension) {
    val compileSdkValue = AndroidConfig.COMPILE_SDK
    val minSdkVersion = AndroidConfig.MIN_SDK

    when (commonExtension) {
        is ApplicationExtension -> {
            commonExtension.compileSdk = compileSdkValue
            commonExtension.defaultConfig.minSdk = minSdkVersion
            commonExtension.compileOptions {
                sourceCompatibility = AndroidConfig.JavaVersion
                targetCompatibility = AndroidConfig.JavaVersion
                isCoreLibraryDesugaringEnabled = true
            }
            commonExtension.lint {
                abortOnError = false
                checkReleaseBuilds = false
                lintConfig = rootProject.file("lint.xml")
                baseline = file("lint-baseline.xml")
                checkDependencies = true
                ignoreTestSources = true
            }
        }

        is LibraryExtension -> {
            commonExtension.compileSdk = compileSdkValue
            commonExtension.defaultConfig.minSdk = minSdkVersion
            commonExtension.compileOptions {
                sourceCompatibility = AndroidConfig.JavaVersion
                targetCompatibility = AndroidConfig.JavaVersion
                isCoreLibraryDesugaringEnabled = true
            }
            commonExtension.lint {
                abortOnError = false
                checkReleaseBuilds = false
                lintConfig = rootProject.file("lint.xml")
                baseline = file("lint-baseline.xml")
                checkDependencies = true
                ignoreTestSources = true
            }
        }

        is TestExtension -> {
            commonExtension.compileSdk = compileSdkValue
            commonExtension.defaultConfig.minSdk = minSdkVersion
            commonExtension.compileOptions {
                sourceCompatibility = AndroidConfig.JavaVersion
                targetCompatibility = AndroidConfig.JavaVersion
                isCoreLibraryDesugaringEnabled = true
            }
            commonExtension.lint {
                abortOnError = false
                checkReleaseBuilds = false
                lintConfig = rootProject.file("lint.xml")
                baseline = file("lint-baseline.xml")
                checkDependencies = true
                ignoreTestSources = true
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(AndroidConfig.JvmTarget)
            freeCompilerArgs.addAll(
                "-Xcontext-parameters",
                "-opt-in=kotlin.RequiresOptIn",
            )

            val warningsAsErrors: String? by project
            allWarningsAsErrors.set(warningsAsErrors.toBoolean())
        }
    }

    dependencies {
        "coreLibraryDesugaring"(libsCatalog.getLib("desugar"))
    }
}

internal fun Project.configureAndroidMultiplatform(androidExtension: KotlinMultiplatformAndroidLibraryExtension) {
    androidExtension.apply {
        compileSdk = AndroidConfig.COMPILE_SDK
        minSdk = AndroidConfig.MIN_SDK

        withHostTest { }
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        lint {
            abortOnError = false
            checkReleaseBuilds = false
            lintConfig = rootProject.file("lint.xml")
            baseline = file("lint-baseline.xml")
            checkDependencies = true
            ignoreTestSources = true
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(AndroidConfig.JvmTarget)
            freeCompilerArgs.addAll(
                "-Xcontext-parameters",
                "-opt-in=kotlin.RequiresOptIn",
            )

            val warningsAsErrors: String? by project
            allWarningsAsErrors.set(warningsAsErrors.toBoolean())
        }
    }
}

internal fun Project.configureCompose(commonExtension: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

    when (commonExtension) {
        is ApplicationExtension -> {
            commonExtension.buildFeatures.compose = true
        }

        is LibraryExtension -> {
            commonExtension.buildFeatures.compose = true
        }
    }

    commonExtension.apply {
        dependencies {
            "implementation"(platform(composeCatalog.getLib("compose-bom")))
        }
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        val enableMetrics = project.providers.gradleProperty("enableComposeCompilerMetrics").orNull.toBoolean()
        val enableReports = project.providers.gradleProperty("enableComposeCompilerReports").orNull.toBoolean()

        val rootBuildDir = rootProject.layout.buildDirectory
        val relativePath = projectDir.relativeTo(rootDir)

        if (enableMetrics) {
            metricsDestination.set(rootBuildDir.dir("compose-metrics").map { it.dir(relativePath.path) })
        }

        if (enableReports) {
            reportsDestination.set(rootBuildDir.dir("compose-reports").map { it.dir(relativePath.path) })
        }

        val stabilityConfig = rootProject.layout.projectDirectory.file("app/compose_stability.conf")
        if (stabilityConfig.asFile.exists()) {
            stabilityConfigurationFiles.add(stabilityConfig)
        }
    }
}

internal fun Project.configureTest() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }
}

val Project.generatedBuildDir: File get() = project.layout.buildDirectory.asFile.get().resolve("generated/ephyra")
