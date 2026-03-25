import ephyra.buildlogic.AndroidConfig
import ephyra.buildlogic.configureAndroid
import ephyra.buildlogic.configureTest

plugins {
    id("com.android.application")
    kotlin("android")

    id("mihon.code.lint")
}

android {
    defaultConfig {
        targetSdk = AndroidConfig.TARGET_SDK
    }
    configureAndroid(this)
    configureTest()
}
