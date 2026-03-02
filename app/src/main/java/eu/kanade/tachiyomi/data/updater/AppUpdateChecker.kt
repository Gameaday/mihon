package eu.kanade.tachiyomi.data.updater

import android.content.Context
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isFossBuildType
import eu.kanade.tachiyomi.util.system.isNightlyBuildType
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.interactor.GetApplicationRelease
import uy.kohesive.injekt.injectLazy

class AppUpdateChecker {

    private val getApplicationRelease: GetApplicationRelease by injectLazy()

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
        isPreviewBuildType -> "mihonapp/mihon-preview"
        isNightlyBuildType -> "Gameaday/mihon"
        else -> "mihonapp/mihon"
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
