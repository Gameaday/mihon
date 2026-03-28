import ephyra.buildlogic.Config

plugins {
    id("ephyra.library")

}

android {
    namespace = "ephyra.telemetry"

    sourceSets {
        getByName("main") {
            if (Config.includeTelemetry) {
                kotlin.srcDirs("src/firebase/kotlin")
            } else {
                kotlin.srcDirs("src/noop/kotlin")
                manifest.srcFile("src/noop/AndroidManifext.xml")
            }
        }
    }
}

dependencies {
    if (Config.includeTelemetry) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
    }
}


