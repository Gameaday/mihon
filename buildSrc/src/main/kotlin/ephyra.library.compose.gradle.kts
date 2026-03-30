import com.android.build.api.dsl.LibraryExtension
import ephyra.buildlogic.configureCompose

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("ephyra.code.lint")
}

extensions.configure<LibraryExtension> {
    configureCompose(this)
}

