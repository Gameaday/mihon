@file:Suppress("UNUSED", "KotlinConstantConditions")

package ephyra.app.util.system

import ephyra.app.BuildConfig

val telemetryIncluded: Boolean
    inline get() = BuildConfig.TELEMETRY_INCLUDED

val updaterEnabled: Boolean
    inline get() = BuildConfig.UPDATER_ENABLED

val isDebugBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "debug"

val isPreviewBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "preview"

val isNightlyBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "nightly"

val isReleaseBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "release"

val isFossBuildType: Boolean
    inline get() = BuildConfig.BUILD_TYPE == "foss"
