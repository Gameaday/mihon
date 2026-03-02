package tachiyomi.domain.release.interactor

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import java.time.Instant
import java.time.temporal.ChronoUnit

class GetApplicationRelease(
    private val service: ReleaseService,
    private val preferenceStore: PreferenceStore,
) {

    private val lastChecked: Preference<Long> by lazy {
        preferenceStore.getLong(Preference.appStateKey("last_app_check"), 0)
    }

    suspend fun await(arguments: Arguments): Result {
        val now = Instant.now()

        // Limit checks to once every 3 days at most
        val nextCheckTime = Instant.ofEpochMilli(lastChecked.get()).plus(3, ChronoUnit.DAYS)
        if (!arguments.forceCheck && now.isBefore(nextCheckTime)) {
            return Result.NoNewUpdate
        }

        val release = service.latest(arguments) ?: return Result.NoNewUpdate

        lastChecked.set(now.toEpochMilli())

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            arguments.isPreview,
            arguments.isNightly,
            arguments.commitCount,
            arguments.commitSha,
            arguments.versionName,
            release.version,
        )
        return when {
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        isPreview: Boolean,
        isNightly: Boolean,
        commitCount: Int,
        commitSha: String,
        versionName: String,
        versionTag: String,
    ): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace(NON_DIGIT_REGEX, "")
        return when {
            isPreview -> {
                // Preview builds: based on releases in "mihonapp/mihon-preview" repo
                // tagged as something like "r1234"
                newVersion.toInt() > commitCount
            }
            isNightly -> {
                // Nightly builds: version is the short git SHA extracted from the release assets.
                // A different SHA means a newer nightly is available.
                versionTag.isNotBlank() && versionTag != commitSha
            }
            else -> {
                // Release builds: based on releases in "mihonapp/mihon" repo
                // tagged as something like "v0.1.2"
                val oldVersion = versionName.replace(NON_DIGIT_REGEX, "")

                val newSemVer = newVersion.split(".").map { it.toInt() }
                val oldSemVer = oldVersion.split(".").map { it.toInt() }

                oldSemVer.mapIndexed { index, i ->
                    if (newSemVer[index] > i) {
                        return true
                    }
                }

                false
            }
        }
    }

    data class Arguments(
        val isFoss: Boolean,
        val isPreview: Boolean,
        val isNightly: Boolean = false,
        val commitCount: Int,
        val commitSha: String = "",
        val versionName: String,
        val repository: String,
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }

    companion object {
        /**
         * Pre-compiled regex that strips non-digit, non-dot characters from version tag strings
         * (e.g. turns "v0.1.2" or "r1234" into "0.1.2" / "1234"). Compiled once at class-load
         * time to avoid the cost of repeated [toRegex] calls inside [isNewVersion].
         */
        private val NON_DIGIT_REGEX = "[^\\d.]".toRegex()
    }
}
