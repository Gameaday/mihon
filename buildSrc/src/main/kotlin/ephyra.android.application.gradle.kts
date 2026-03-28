import com.android.build.api.dsl.ApplicationExtension
import ephyra.buildlogic.AndroidConfig
import ephyra.buildlogic.configureAndroid
import ephyra.buildlogic.configureTest

plugins {
    id("com.android.application")

    id("ephyra.code.lint")
}

extensions.configure<ApplicationExtension> {
    defaultConfig {
        targetSdk = AndroidConfig.TARGET_SDK
    }
    configureAndroid(this)
}

configureTest()

