package ephyra.app.data.updater

import android.content.Context
import ephyra.app.BuildConfig
import ephyra.app.util.system.isFossBuildType
import ephyra.app.util.system.isNightlyBuildType
import ephyra.app.util.system.isPreviewBuildType
import ephyra.core.common.util.lang.withIOContext
import ephyra.domain.release.interactor.GetApplicationRelease

class AppUpdateChecker(
    private val getApplicationRelease: GetApplicationRelease,
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
                is GetApplicationRelease.Result.NewUpdate -> AppUpdateNotifier(context).promptUpdate(result.release)
                else -> {}
            }

            result
        }
    }
}

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
