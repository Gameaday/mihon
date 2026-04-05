package ephyra.data.updater

import android.content.Context
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.data.BuildConfig
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.release.service.AppUpdateNotifier

class AppUpdateChecker(
    private val getApplicationRelease: GetApplicationRelease,
    private val notifier: AppUpdateNotifier,
) {

    suspend fun checkForUpdate(context: Context, forceCheck: Boolean = false): GetApplicationRelease.Result {
        return withIOContext {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    isFoss = isFossBuildType,
                    isPreview = isPreviewBuildType,
                    isNightly = isNightlyBuildType,
                    commitCount = BuildConfig.COMMIT_COUNT.toInt(),
                    commitSha = BuildConfig.COMMIT_SHA,
                    versionName = BuildConfig.VERSION_NAME,
                    repository = GITHUB_REPO,
                    forceCheck = forceCheck,
                ),
            )

            when (result) {
                is GetApplicationRelease.Result.NewUpdate -> notifier.promptUpdate(result.release)
                else -> {}
            }

            result
        }
    }
}

private val isFossBuildType: Boolean = false // Placeholder
private val isPreviewBuildType: Boolean = false // Placeholder
private val isNightlyBuildType: Boolean = false // Placeholder

val GITHUB_REPO: String by lazy {
    when {
        isPreviewBuildType -> "Gameaday/Ephyra-preview"
        isNightlyBuildType -> "Gameaday/Ephyra"
        else -> "Gameaday/Ephyra"
    }
}

val RELEASE_TAG: String by lazy {
    if (isPreviewBuildType) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }
}

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
