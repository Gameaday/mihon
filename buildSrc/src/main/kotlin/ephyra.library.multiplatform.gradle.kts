import ephyra.buildlogic.configureAndroidMultiplatform
import ephyra.buildlogic.configureTest

plugins {
    id("com.android.kotlin.multiplatform.library")
    kotlin("multiplatform")

    id("ephyra.code.lint")
}

kotlin {
    android {
        configureAndroidMultiplatform(this)
    }

    configureTest()
}


