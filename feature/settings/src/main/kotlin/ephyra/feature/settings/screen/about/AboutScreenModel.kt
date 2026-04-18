package ephyra.feature.settings.screen.about

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.toDateTimestampString
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.ui.UiPreferences
import ephyra.presentation.core.ui.AppInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class AboutScreenModel(
    private val getApplicationRelease: GetApplicationRelease,
    private val uiPreferences: UiPreferences,
    private val appInfo: AppInfo,
) : StateScreenModel<AboutScreenState>(AboutScreenState()) {

    private val _events: Channel<AboutEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    fun checkVersion() {
        if (state.value.isCheckingUpdates) return

        mutableState.update { it.copy(isCheckingUpdates = true) }

        screenModelScope.launchIO {
            try {
                val result = getApplicationRelease.await(
                    GetApplicationRelease.Arguments(
                        isFoss = appInfo.isFoss,
                        isPreview = appInfo.isPreview,
                        isNightly = appInfo.isNightly,
                        commitCount = appInfo.commitCount.toIntOrNull() ?: 0,
                        commitSha = appInfo.commitSha,
                        versionName = appInfo.versionName,
                        repository = appInfo.githubRepo,
                        forceCheck = true,
                    ),
                )
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
            appInfo.isDebug -> {
                "Debug ${appInfo.commitSha}".let {
                    if (withBuildDate) {
                        "$it (${getFormattedBuildTime()})"
                    } else {
                        it
                    }
                }
            }

            appInfo.isPreview -> {
                "Beta r${appInfo.commitCount}".let {
                    if (withBuildDate) {
                        "$it (${appInfo.commitSha}, ${getFormattedBuildTime()})"
                    } else {
                        "$it (${appInfo.commitSha})"
                    }
                }
            }

            appInfo.isNightly -> {
                "Ephyra ${appInfo.versionName}".let {
                    if (withBuildDate) {
                        "$it (${appInfo.commitSha}, ${getFormattedBuildTime()})"
                    } else {
                        "$it (${appInfo.commitSha})"
                    }
                }
            }

            else -> {
                "Stable ${appInfo.versionName}".let {
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
                Instant.parse(appInfo.buildTime),
                ZoneId.systemDefault(),
            )
                .toDateTimestampString(
                    UiPreferences.dateFormat(
                        uiPreferences.dateFormat().getSync(),
                    ),
                )
        } catch (e: Exception) {
            appInfo.buildTime
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
