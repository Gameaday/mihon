package ephyra.app.ui.main

import android.app.SearchManager
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.domain.base.BasePreferences
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.presentation.components.AppStateBanners
import ephyra.presentation.components.DownloadedOnlyBannerBackgroundColor
import ephyra.presentation.components.IncognitoModeBannerBackgroundColor
import ephyra.presentation.components.IndexingBannerBackgroundColor
import ephyra.presentation.more.settings.screen.browse.ExtensionReposScreen
import ephyra.presentation.more.settings.screen.data.RestoreBackupScreen
import ephyra.presentation.util.AssistContentScreen
import ephyra.presentation.util.DefaultNavigatorScreenTransition
import ephyra.app.BuildConfig
import ephyra.app.data.cache.ChapterCache
import ephyra.app.data.download.DownloadCache
import ephyra.app.data.notification.NotificationReceiver
import ephyra.app.data.updater.AppUpdateChecker
import ephyra.app.data.updater.RELEASE_URL
import ephyra.app.extension.api.ExtensionApi
import ephyra.presentation.core.ui.activity.BaseActivity
import ephyra.app.ui.browse.source.authority.MatchResultsScreen
import ephyra.app.ui.browse.source.browse.BrowseSourceScreen
import ephyra.app.ui.browse.source.globalsearch.GlobalSearchScreen
import ephyra.app.ui.deeplink.DeepLinkScreen
import ephyra.app.ui.home.HomeScreen
import ephyra.app.ui.manga.MangaScreen
import ephyra.app.ui.more.NewUpdateScreen
import ephyra.app.ui.more.OnboardingScreen
import ephyra.app.util.system.dpToPx
import ephyra.app.util.system.isNavigationBarNeedsScrim
import ephyra.app.util.system.openInBrowser
import ephyra.app.util.system.updaterEnabled
import ephyra.app.util.view.setComposeContent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import ephyra.core.migration.Migrator
import ephyra.core.common.Constants
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.system.logcat
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.i18n.MR
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.collectAsState
import org.koin.android.ext.android.inject

class MainActivity : BaseActivity() {

    private val libraryPreferences: ephyra.domain.library.service.LibraryPreferences by inject()
    private val preferences: ephyra.domain.base.BasePreferences by inject()

    private val downloadCache: ephyra.app.data.download.DownloadCache by inject()
    private val chapterCache: ephyra.app.data.cache.ChapterCache by inject()

    private val getIncognitoState: ephyra.domain.source.interactor.GetIncognitoState by inject()
    private val uiPreferences: ephyra.domain.ui.UiPreferences by inject()
    private val privacyPreferences: ephyra.app.core.security.PrivacyPreferences by inject()
    private val storagePreferences: ephyra.domain.storage.service.StoragePreferences by inject()
    private val extensionApi: ExtensionApi by inject()
    private val appUpdateChecker: AppUpdateChecker by inject()

    // To be checked by splash screen. If true then splash screen will be removed.
    var ready = false

    private var navigator: Navigator? = null

    init {
        registerSecureActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isLaunch = savedInstanceState == null

        // Prevent splash screen showing up on configuration changes
        val splashScreen = if (isLaunch) installSplashScreen() else null

        super.onCreate(savedInstanceState)

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        setComposeContent {
            androidx.compose.runtime.CompositionLocalProvider(
                ephyra.presentation.util.LocalUiPreferences provides uiPreferences,
                ephyra.presentation.util.LocalBasePreferences provides preferences,
                ephyra.presentation.util.LocalPrivacyPreferences provides privacyPreferences,
                ephyra.presentation.util.LocalLibraryPreferences provides libraryPreferences,
                ephyra.presentation.util.LocalStoragePreferences provides storagePreferences,
            ) {
                var didMigration by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) {
                didMigration = Migrator.awaitAndRelease()
            }

            val context = LocalContext.current

            var incognito by remember { mutableStateOf(getIncognitoState.await(null)) }
            val downloadOnly by preferences.downloadedOnly().collectAsStateWithLifecycle()
            val indexing by downloadCache.isInitializing.collectAsStateWithLifecycle()

            val isSystemInDarkTheme = isSystemInDarkTheme()
            val statusBarBackgroundColor = when {
                indexing -> IndexingBannerBackgroundColor
                downloadOnly -> DownloadedOnlyBannerBackgroundColor
                incognito -> IncognitoModeBannerBackgroundColor
                else -> MaterialTheme.colorScheme.surface
            }
            LaunchedEffect(isSystemInDarkTheme, statusBarBackgroundColor) {
                // Draw edge-to-edge and set system bars color to transparent
                val lightStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK)
                val darkStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(
                    statusBarStyle = if (statusBarBackgroundColor.luminance() > 0.5) lightStyle else darkStyle,
                    navigationBarStyle = if (isSystemInDarkTheme) darkStyle else lightStyle,
                )
            }

            Navigator(
                screen = HomeScreen,
                disposeBehavior = NavigatorDisposeBehavior(disposeNestedNavigators = false, disposeSteps = true),
            ) { navigator ->
                LaunchedEffect(navigator) {
                    this@MainActivity.navigator = navigator

                    if (isLaunch) {
                        // Set start screen
                        handleIntentAction(intent, navigator)

                        // Reset Incognito Mode on relaunch
                        preferences.incognitoMode().set(false)
                    }
                }
                LaunchedEffect(navigator.lastItem) {
                    (navigator.lastItem as? BrowseSourceScreen)?.sourceId
                        .let(getIncognitoState::subscribe)
                        .collectLatest { incognito = it }
                }

                val scaffoldInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                Scaffold(
                    topBar = {
                        AppStateBanners(
                            downloadedOnlyMode = downloadOnly,
                            incognitoMode = incognito,
                            indexing = indexing,
                            modifier = Modifier.windowInsetsPadding(scaffoldInsets),
                        )
                    },
                    contentWindowInsets = scaffoldInsets,
                ) { contentPadding ->
                    // Consume insets already used by app state banners
                    Box {
                        // Shows current screen
                        DefaultNavigatorScreenTransition(
                            navigator = navigator,
                            modifier = Modifier
                                .padding(contentPadding)
                                .consumeWindowInsets(contentPadding),
                        )

                        // Draw navigation bar scrim when needed
                        if (remember { isNavigationBarNeedsScrim() }) {
                            Spacer(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                    .alpha(0.8f)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                            )
                        }
                    }
                }

                // Pop source-related screens when incognito mode is turned off
                LaunchedEffect(Unit) {
                    preferences.incognitoMode().changes()
                        .drop(1)
                        .filter { !it }
                        .onEach {
                            val currentScreen = navigator.lastItem
                            if (currentScreen is BrowseSourceScreen ||
                                (currentScreen is MangaScreen && currentScreen.fromSource)
                            ) {
                                navigator.popUntilRoot()
                            }
                        }
                        .launchIn(this)
                }

                HandleOnNewIntent(context = context, navigator = navigator)

                CheckForUpdates()
                ShowOnboarding()
            }

            var showChangelog by remember { mutableStateOf(didMigration == true && !BuildConfig.DEBUG) }
            if (showChangelog) {
                AlertDialog(
                    onDismissRequest = { showChangelog = false },
                    title = { Text(text = stringResource(MR.strings.updated_version, BuildConfig.VERSION_NAME)) },
                    dismissButton = {
                        TextButton(onClick = { openInBrowser(RELEASE_URL) }) {
                            Text(text = stringResource(MR.strings.whats_new))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showChangelog = false }) {
                            Text(text = stringResource(MR.strings.action_ok))
                        }
                    },
                )
            }
        }

        val startTime = System.currentTimeMillis()
        splashScreen?.setKeepOnScreenCondition {
            val elapsed = System.currentTimeMillis() - startTime
            elapsed <= SPLASH_MIN_DURATION || (!ready && elapsed <= SPLASH_MAX_DURATION)
        }
        setSplashScreenExitAnimation(splashScreen)

        if (isLaunch) {
            lifecycleScope.launchIO {
                if (libraryPreferences.autoClearChapterCache().get()) {
                    chapterCache.clear()
                }
            }
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        when (val screen = navigator?.lastItem) {
            is AssistContentScreen -> {
                screen.onProvideAssistUrl()?.let { outContent.webUri = it.toUri() }
            }
        }
    }

    @Composable
    private fun HandleOnNewIntent(context: Context, navigator: Navigator) {
        LaunchedEffect(Unit) {
            callbackFlow {
                val componentActivity = context as ComponentActivity
                val consumer = Consumer<Intent> { trySend(it) }
                componentActivity.addOnNewIntentListener(consumer)
                awaitClose { componentActivity.removeOnNewIntentListener(consumer) }
            }
                .collectLatest { handleIntentAction(it, navigator) }
        }
    }

    @Composable
    private fun CheckForUpdates() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        // App updates
        LaunchedEffect(Unit) {
            if (updaterEnabled) {
                try {
                    val result = appUpdateChecker.checkForUpdate(context)
                    if (result is GetApplicationRelease.Result.NewUpdate) {
                        val updateScreen = NewUpdateScreen(
                            versionName = result.release.version,
                            changelogInfo = result.release.info,
                            releaseLink = result.release.releaseLink,
                            downloadLink = result.release.downloadLink,
                        )
                        navigator.push(updateScreen)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                }
            }
        }

        // Extensions updates
        LaunchedEffect(Unit) {
            try {
                extensionApi.checkForUpdates(context)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    @Composable
    private fun ShowOnboarding() {
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            if (!preferences.shownOnboardingFlow().get() && navigator.lastItem !is OnboardingScreen) {
                navigator.push(OnboardingScreen())
            }
        }
    }

    /**
     * Splash screen exit animation handling.
     *
     * On API 31+ the system handles the splash screen exit animation natively,
     * so this is a no-op.
     */
    private fun setSplashScreenExitAnimation(splashScreen: SplashScreen?) {
        // No-op: native splash exit animation on API 34+
    }

    private fun handleIntentAction(intent: Intent, navigator: Navigator): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(
                applicationContext,
                notificationId,
                intent.getIntExtra("groupId", 0),
            )
        }

        val tabToOpen = when (intent.action) {
            Constants.SHORTCUT_LIBRARY -> HomeScreen.Tab.Library()
            Constants.SHORTCUT_MANGA -> {
                val idToOpen = intent.extras?.getLong(Constants.MANGA_EXTRA) ?: return false
                navigator.popUntilRoot()
                HomeScreen.Tab.Library(idToOpen)
            }
            Constants.SHORTCUT_UPDATES -> HomeScreen.Tab.Updates
            Constants.SHORTCUT_HISTORY -> HomeScreen.Tab.History
            Constants.SHORTCUT_SOURCES -> HomeScreen.Tab.Browse(false)
            Constants.SHORTCUT_EXTENSIONS -> HomeScreen.Tab.Browse(true)
            Constants.SHORTCUT_DOWNLOADS -> {
                navigator.popUntilRoot()
                HomeScreen.Tab.More(toDownloads = true)
            }
            Constants.SHORTCUT_MATCH_RESULTS -> {
                navigator.popUntilRoot()
                navigator.push(MatchResultsScreen())
                null
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                // Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!query.isNullOrEmpty()) {
                    navigator.popUntilRoot()
                    navigator.push(DeepLinkScreen(query))
                }
                null
            }
            INTENT_SEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (!query.isNullOrEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    navigator.popUntilRoot()
                    navigator.push(GlobalSearchScreen(query, filter))
                }
                null
            }
            Intent.ACTION_VIEW -> {
                // Handling opening of backup files
                if (intent.data.toString().endsWith(".tachibk")) {
                    navigator.popUntilRoot()
                    navigator.push(RestoreBackupScreen(intent.data.toString()))
                }
                // Deep link to add extension repo
                else if (intent.scheme == "tachiyomi" && intent.data?.host == "add-repo") {
                    intent.data?.getQueryParameter("url")?.let { repoUrl ->
                        navigator.popUntilRoot()
                        navigator.push(ExtensionReposScreen(repoUrl))
                    }
                }
                null
            }
            else -> return false
        }

        if (tabToOpen != null) {
            lifecycleScope.launch { HomeScreen.openTab(tabToOpen) }
        }

        ready = true
        return true
    }

    companion object {
        const val INTENT_SEARCH = "ephyra.app.SEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"

        // Splash screen
        private const val SPLASH_MIN_DURATION = 500 // ms
        private const val SPLASH_MAX_DURATION = 5000 // ms
    }
}
