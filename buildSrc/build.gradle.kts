import org.gradle.internal.impldep.org.eclipse.jgit.diff.Subsequence.a

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(androidx.gradle)
    implementation(kotlinx.gradle)
    implementation(libs.kotlin.compose.gradle)
    implementation(libs.spotless.gradle)
    implementation(gradleApi())
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}
