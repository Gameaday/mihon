package ephyra.presentation.core.ui

/**
 * Provides app-level build information to feature modules without requiring
 * a direct dependency on `:app`'s generated `BuildConfig`.
 *
 * Bind a concrete implementation in the application's DI module.
 */
interface AppInfo {
    /** True when running a debug build. */
    val isDebug: Boolean

    /** Build type string e.g. "debug", "release", "preview", "nightly", "foss". */
    val buildType: String

    /** Short commit hash included in the build. */
    val commitSha: String

    /** Monotonically increasing commit count for pre-release builds. */
    val commitCount: String

    /** Semantic version name e.g. "1.2.0". */
    val versionName: String

    /** ISO-8601 build timestamp. */
    val buildTime: String

    val isPreview: Boolean get() = buildType == "preview"
    val isNightly: Boolean get() = buildType == "nightly"
    val isRelease: Boolean get() = buildType == "release"
    val isFoss: Boolean get() = buildType == "foss"
    val telemetryIncluded: Boolean
    val updaterEnabled: Boolean
}
