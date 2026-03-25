import ephyra.buildlogic.configureAndroid
import ephyra.buildlogic.configureTest

plugins {
    id("com.android.test")
    kotlin("android")

    id("mihon.code.lint")
}

android {
    configureAndroid(this)
    configureTest()
}
