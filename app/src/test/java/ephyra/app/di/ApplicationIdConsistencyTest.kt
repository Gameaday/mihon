package ephyra.app.di

import ephyra.app.BuildConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Build-time guard that enforces [BuildConfig.APPLICATION_ID] follows the standard Java/Android
 * all-lowercase package naming convention.
 *
 * ### Why this test exists
 * Android package names are case-sensitive at the Java/Kotlin level (they map to filesystem
 * directories on Linux). [BuildConfig.APPLICATION_ID] is baked into every build by the Android
 * Gradle plugin; any mixed-case typo in `app/build.gradle.kts` → `applicationId = "..."` is
 * silently propagated to the runtime `context.packageName`, the `TelemetryConfig` allowlist,
 * the DataStore file name, and every broadcast action string that embeds the ID.
 *
 * When the casing is wrong, failures are silent and hard to diagnose:
 *  - Telemetry never initialises (isEphyraProductionApp() returns false every time).
 *  - Broadcast receivers registered with an action derived from APPLICATION_ID may silently
 *    not fire, or fire for the wrong receiver if the string doesn't match what was registered.
 *
 * This test catches the mismatch **before the app ships** by failing the unit-test step of CI.
 *
 * ### What this test enforces
 * - The base (release) APPLICATION_ID must be exactly `"app.ephyra"` (all lowercase).
 *   The debug / nightly / preview build types append suffixes (`.dev`, `.nightly`, `.debug`,
 *   `.foss`, `.benchmark`) via `applicationIdSuffix`; those are not checked here because
 *   [BuildConfig.APPLICATION_ID] in a JVM unit test reflects the `debug` variant's value
 *   (`"app.ephyra.dev"`), which also starts with the correct lowercase base.
 * - The ID must start with `"app.ephyra"` — covering both the base release ID and all
 *   suffixed variant IDs in a single assertion.
 *
 * @see ephyra.telemetry.TelemetryConfig
 */
@Execution(ExecutionMode.CONCURRENT)
class ApplicationIdConsistencyTest {

    /**
     * Verifies that [BuildConfig.APPLICATION_ID] begins with the all-lowercase canonical
     * base `"app.ephyra"`.
     *
     * All six build variants share this prefix:
     * - `app.ephyra`          (release)
     * - `app.ephyra.dev`      (debug)
     * - `app.ephyra.nightly`  (nightly)
     * - `app.ephyra.debug`    (preview — the signed test build)
     * - `app.ephyra.foss`     (foss)
     * - `app.ephyra.benchmark`(benchmark)
     *
     * If this test fails, update `applicationId` in `app/build.gradle.kts` to be
     * all-lowercase and update `APPLICATION_ID` in `core/data/build.gradle.kts` to match.
     */
    @Test
    fun `APPLICATION_ID starts with all-lowercase canonical base`() {
        val id = BuildConfig.APPLICATION_ID
        assertEquals(
            true,
            id.startsWith("app.ephyra"),
            "BuildConfig.APPLICATION_ID must start with \"app.ephyra\" (all lowercase). " +
                "Found: \"$id\". " +
                "Fix: set applicationId = \"app.ephyra\" in app/build.gradle.kts " +
                "and update APPLICATION_ID in core/data/build.gradle.kts.",
        )
    }

    /**
     * Verifies that [BuildConfig.APPLICATION_ID] contains no uppercase letters.
     *
     * Java/Android convention (JLS §7.1, Android developer guide) mandates all-lowercase
     * package components. Uppercase in APPLICATION_ID causes silent mismatches in any code
     * that derives strings from it (broadcast actions, TelemetryConfig allowlist, etc.).
     */
    @Test
    fun `APPLICATION_ID contains no uppercase letters`() {
        val id = BuildConfig.APPLICATION_ID
        assertEquals(
            id,
            id.lowercase(),
            "BuildConfig.APPLICATION_ID must be all-lowercase per Java/Android naming convention. " +
                "Found uppercase characters in: \"$id\". " +
                "Fix: set applicationId = \"app.ephyra\" in app/build.gradle.kts.",
        )
    }
}
