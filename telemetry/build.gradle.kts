import ephyra.buildlogic.Config

plugins {
    id("ephyra.library")
}

android {
    namespace = "ephyra.telemetry"

    sourceSets {
        getByName("main") {
            // FIX: Modern AGP 9.1 syntax
            java.directories.add(file("src/main/kotlin").toString())
            res.directories.add(file("src/main/res").toString())
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
