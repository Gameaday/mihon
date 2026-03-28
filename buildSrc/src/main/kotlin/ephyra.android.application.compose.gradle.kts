import com.android.build.api.dsl.ApplicationExtension
import ephyra.buildlogic.configureCompose

plugins {
    id("com.android.application")

    id("ephyra.code.lint")
}

extensions.configure<ApplicationExtension> {
    configureCompose(this)
}

