import com.android.build.api.dsl.LibraryExtension
import ephyra.buildlogic.configureCompose

plugins {
    id("com.android.library")

    id("ephyra.code.lint")
}

extensions.configure<LibraryExtension> {
    configureCompose(this)
}

