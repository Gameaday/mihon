package ephyra.feature.settings.screen.about

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.core.common.util.lang.launchUI
import ephyra.core.common.util.lang.toDateTimestampString
import ephyra.core.common.util.system.logcat
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.ui.UiPreferences
import ephyra.feature.settings.widget.TextPreferenceWidget
import ephyra.i18n.MR
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.LinkIcon
import ephyra.presentation.core.components.LogoHeader
import ephyra.presentation.core.components.ScrollbarLazyColumn
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.icons.CustomIcons
import ephyra.presentation.core.icons.Discord
import ephyra.presentation.core.icons.Github
import ephyra.presentation.core.ui.AppInfo
import ephyra.presentation.core.ui.NewUpdateScreenFactory
import ephyra.presentation.core.util.CrashLogUtil
import ephyra.presentation.core.util.LocalBackPress
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.system.copyToClipboard
import ephyra.presentation.core.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import logcat.LogPriority
import org.koin.compose.koinInject

object AboutScreen : Screen() {

    @Composable
    override fun Content() {
        val screenModel = koinScreenModel<AboutScreenModel>()
        val state by screenModel.state.collectAsState()
        val appInfo: AppInfo = koinInject()
        val newUpdateScreenFactory: NewUpdateScreenFactory = koinInject()
        val extensionManager: ExtensionManager = koinInject()

        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val handleBack = LocalBackPress.current
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    is AboutEvent.NewUpdate -> {
                        val updateScreen = newUpdateScreenFactory.create(
                            versionName = event.result.release.version,
                            changelogInfo = event.result.release.info,
                            releaseLink = event.result.release.releaseLink,
                            downloadLink = event.result.release.downloadLink,
                        )
                        navigator.push(updateScreen)
                    }

                    is AboutEvent.UpdateError -> {
                        context.toast(event.error.message)
                        logcat(LogPriority.ERROR, event.error)
                    }
                }
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_category_about),
                    navigateUp = if (handleBack != null) handleBack::invoke else null,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            ScrollbarLazyColumn(
                contentPadding = contentPadding,
            ) {
                item {
                    LogoHeader()
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.version),
                        subtitle = screenModel.getVersionName(withBuildDate = true),
                        onPreferenceClick = {
                            val deviceInfo = CrashLogUtil(context, extensionManager).getDebugInfo()
                            context.copyToClipboard("Debug information", deviceInfo)
                        },
                    )
                }

                if (appInfo.updaterEnabled) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.check_for_updates),
                            widget = {
                                AnimatedVisibility(visible = state.isCheckingUpdates) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp,
                                    )
                                }
                            },
                            onPreferenceClick = { screenModel.checkVersion() },
                        )
                    }
                }

                if (!appInfo.isDebug) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.whats_new),
                            onPreferenceClick = { uriHandler.openUri(appInfo.releaseUrl) },
                        )
                    }
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.licenses),
                        onPreferenceClick = { navigator.push(OpenSourceLicensesScreen()) },
                    )
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.privacy_policy),
                        onPreferenceClick = {
                            uriHandler.openUri("https://github.com/Gameaday/Ephyra/blob/main/PRIVACY.md")
                        },
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        LinkIcon(
                            label = stringResource(MR.strings.website),
                            icon = Icons.Outlined.Public,
                            url = "https://github.com/Gameaday/Ephyra",
                        )
                        LinkIcon(
                            label = "GitHub",
                            icon = CustomIcons.Github,
                            url = "https://github.com/Gameaday/Ephyra",
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun getVersionName(withBuildDate: Boolean): String {
        val screenModel = koinScreenModel<AboutScreenModel>()
        return screenModel.getVersionName(withBuildDate)
    }

    @Composable
    fun getFormattedBuildTime(): String {
        val appInfo: AppInfo = koinInject()
        val uiPreferences: UiPreferences = koinInject()
        return try {
            val fmt = uiPreferences.dateFormat().getSync()
            val dt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.parse(appInfo.buildTime),
                java.time.ZoneId.systemDefault(),
            )
            dt.toDateTimestampString(UiPreferences.dateFormat(fmt))
        } catch (e: Exception) {
            appInfo.buildTime
        }
    }
}
