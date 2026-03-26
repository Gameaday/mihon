package ephyra.presentation.more.settings.screen.about

import android.content.Context
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.app.BuildConfig
import ephyra.app.data.updater.AppUpdateChecker
import ephyra.app.util.lang.toDateTimestampString
import ephyra.app.util.system.isNightlyBuildType
import ephyra.app.util.system.isPreviewBuildType
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.ui.UiPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class AboutScreenModel(
    private val appUpdateChecker: AppUpdateChecker,
    private val uiPreferences: UiPreferences,
) : StateScreenModel<AboutScreenState>(AboutScreenState()) {

    private val _events: Channel<AboutEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    fun checkVersion(context: Context) {
        if (state.value.isCheckingUpdates) return

        mutableState.update { it.copy(isCheckingUpdates = true) }

        screenModelScope.launchIO {
            try {
                val result = appUpdateChecker.checkForUpdate(context, forceCheck = true)
                if (result is GetApplicationRelease.Result.NewUpdate) {
                    _events.send(AboutEvent.NewUpdate(result))
                }
                mutableState.update { it.copy(updateResult = result) }
            } catch (e: Exception) {
                _events.send(AboutEvent.UpdateError(e))
            } finally {
                mutableState.update { it.copy(isCheckingUpdates = false) }
            }
        }
    }

    fun getVersionName(withBuildDate: Boolean): String {
        return when {
            BuildConfig.DEBUG -> {
                "Debug ${BuildConfig.COMMIT_SHA}".let {
                    if (withBuildDate) {
                        "$it (${getFormattedBuildTime()})"
                    } else {
                        it
                    }
                }
            }
            isPreviewBuildType -> {
                "Beta r${BuildConfig.COMMIT_COUNT}".let {
                    if (withBuildDate) {
                        "$it (${BuildConfig.COMMIT_SHA}, ${getFormattedBuildTime()})"
                    } else {
                        "$it (${BuildConfig.COMMIT_SHA})"
                    }
                }
            }
            isNightlyBuildType -> {
                "Ephyra ${BuildConfig.VERSION_NAME}".let {
                    if (withBuildDate) {
                        "$it (${BuildConfig.COMMIT_SHA}, ${getFormattedBuildTime()})"
                    } else {
                        "$it (${BuildConfig.COMMIT_SHA})"
                    }
                }
            }
            else -> {
                "Stable ${BuildConfig.VERSION_NAME}".let {
                    if (withBuildDate) {
                        "$it (${getFormattedBuildTime()})"
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun getFormattedBuildTime(): String {
        return try {
            LocalDateTime.ofInstant(
                Instant.parse(BuildConfig.BUILD_TIME),
                ZoneId.systemDefault(),
            )
                .toDateTimestampString(
                    UiPreferences.dateFormat(
                        uiPreferences.dateFormat().get(),
                    ),
                )
        } catch (e: Exception) {
            BuildConfig.BUILD_TIME
        }
    }
}

sealed class AboutEvent {
    data class NewUpdate(val result: GetApplicationRelease.Result.NewUpdate) : AboutEvent()
    data class UpdateError(val error: Throwable) : AboutEvent()
}

@Immutable
data class AboutScreenState(
    val isCheckingUpdates: Boolean = false,
    val updateResult: GetApplicationRelease.Result? = null,
)
