package ephyra.app

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Looper
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import androidx.work.WorkerFactory
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.util.DebugLogger
import ephyra.app.R
import ephyra.app.crash.CrashActivity
import ephyra.app.crash.GlobalExceptionHandler
import ephyra.app.crash.StartupFailureActivity
import ephyra.app.di.koinAppModule
import ephyra.app.di.koinAppModule_UI
import ephyra.app.di.koinPreferenceModule
import ephyra.app.startup.StartupTracker
import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.system.DeviceUtil
import ephyra.core.common.util.system.GLUtil
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.WebViewUtil
import ephyra.core.common.util.system.cancelNotification
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.notify
import ephyra.core.migration.Migrator
import ephyra.core.migration.migrations.migrations
import ephyra.data.coil.BufferedSourceFetcher
import ephyra.data.coil.MangaCoverFetcher
import ephyra.data.coil.MangaCoverKeyer
import ephyra.data.coil.MangaKeyer
import ephyra.data.notification.Notifications
import ephyra.domain.base.BasePreferences
import ephyra.domain.koinDomainModule
import ephyra.domain.ui.UiPreferences
import ephyra.domain.ui.model.ThemeMode
import ephyra.domain.ui.model.setAppCompatDelegateThemeMode
import ephyra.i18n.MR
import ephyra.presentation.core.data.coil.TachiyomiImageDecoder
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate
import ephyra.presentation.core.ui.delegate.SecureActivityDelegateState
import ephyra.presentation.widget.WidgetManager
import ephyra.telemetry.TelemetryConfig
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class App : Application(), Configuration.Provider, DefaultLifecycleObserver, SingletonImageLoader.Factory {

    private val basePreferences: BasePreferences by inject()
    private val privacyPreferences: PrivacyPreferences by inject()
    private val networkPreferences: NetworkPreferences by inject()

    private val disableIncognitoReceiver = DisableIncognitoReceiver()

    /**
     * Provides the WorkManager [Configuration] on demand (lazy initialization).
     * Called by WorkManager the first time [WorkManager.getInstance] is invoked.
     * Koin must be started (via [startKoin]) before this is called; since Koin
     * is started in [onCreate] and WorkManager.getInstance() is only called after
     * that, the [WorkerFactory] bean is always available here.
     */
    override val workManagerConfiguration: Configuration
        get() {
            val config = Configuration.Builder()
                .setWorkerFactory(get<WorkerFactory>())
                .build()
            // Mark WorkManager as configured; recorded the first time WorkManager requests
            // the Configuration (lazy initialisation by WorkManager itself).
            StartupTracker.complete(StartupTracker.Phase.WORKMANAGER_CONFIGURED)
            return config
        }

    @SuppressLint("LaunchActivityFromNotification")
    override fun onCreate() {
        super<Application>.onCreate()
        StartupTracker.complete(StartupTracker.Phase.APP_CREATED)

        // Install the logger as early as possible so every subsequent logcat() call —
        // including those inside startKoin(), StartupTracker, and any catch blocks — is
        // captured rather than silently dropped.  Without this, the entire window from
        // APP_CREATED through KOIN_INITIALIZED is a "log blackout", making cold-start and
        // boot-failure diagnosis blind.  We pick the right minimum priority immediately
        // (no Koin needed) and upgrade to VERBOSE after Koin if the user opted in.
        if (!LogcatLogger.isInstalled) {
            LogcatLogger.install()
            LogcatLogger.loggers += AndroidLogcatLogger(if (BuildConfig.DEBUG) LogPriority.DEBUG else LogPriority.INFO)
        }

        // Install the crash handler first so any subsequent failure in onCreate is
        // captured and surfaced via CrashActivity instead of dying silently.
        GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)

        // Initialise telemetry off the main thread: Firebase's initializeApp performs
        // file I/O and service lookups that can block the main thread for hundreds of
        // milliseconds on cold starts, contributing to ANR on slow devices.
        // The try-catch is required: an unhandled exception from a child coroutine of
        // lifecycleScope (backed by SupervisorJob) propagates to the thread's uncaught-
        // exception handler (GlobalExceptionHandler).  After startKoin() succeeds, Koin
        // is available, so GlobalExceptionHandler would launch CrashActivity — crashing
        // the app because of a non-critical telemetry failure (e.g. missing
        // google-services.json in a fork/test build).  Swallowing the error here keeps
        // the app alive; telemetry will simply be inactive for that session.
        val scope = ProcessLifecycleOwner.get().lifecycleScope
        scope.launch(Dispatchers.IO) {
            try {
                TelemetryConfig.init(applicationContext)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Telemetry initialization failed; telemetry will be inactive" }
            }
        }

        // Avoid potential crashes from multiple WebView processes
        val process = getProcessName()
        if (packageName != process) WebView.setDataDirectorySuffix(process)

        try {
            startKoin {
                androidContext(this@App)
                workManagerFactory()
                // koinPreferenceModule is placed before koinDomainModule so that all
                // preference bindings are registered before any domain single{} block
                // could (in future) reference them during construction.
                modules(koinAppModule, koinPreferenceModule, koinDomainModule, koinAppModule_UI)
            }
        } catch (e: Exception) {
            // startKoin() threw before Koin was fully initialised.  GlobalExceptionHandler
            // cannot open CrashActivity here because that activity's BaseActivity superclass
            // calls KoinJavaComponent.get() eagerly — doing so before startKoin() completes
            // would cause a second crash that swallows the original error and shows no UI.
            //
            // Instead we launch StartupFailureActivity directly (zero Koin dependencies,
            // plain Android Views) and then terminate the process.  This guarantees the
            // user always sees a readable error screen instead of a blank ANR dialog.
            StartupTracker.recordError(StartupTracker.Phase.KOIN_INITIALIZED, e)
            val intent = Intent(applicationContext, StartupFailureActivity::class.java).apply {
                putExtra(StartupFailureActivity.EXTRA_STACK_TRACE, e.stackTraceToString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            applicationContext.startActivity(intent)
            // exitProcess() triggers JVM shutdown hooks before terminating, giving
            // the runtime more opportunity to flush the pending Binder IPC that carries
            // the StartupFailureActivity intent to the system server.  This is safer
            // than the direct killProcess() + sleep pattern which relies on a fixed
            // timing guess.
            kotlin.system.exitProcess(1)
        }
        StartupTracker.complete(StartupTracker.Phase.KOIN_INITIALIZED)

        setupNotificationChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Show notification to disable Incognito Mode when it's enabled
        basePreferences.incognitoMode().changes()
            .onEach { enabled ->
                if (enabled) {
                    disableIncognitoReceiver.register()
                    notify(
                        Notifications.ID_INCOGNITO_MODE,
                        Notifications.CHANNEL_INCOGNITO_MODE,
                    ) {
                        setContentTitle(stringResource(MR.strings.pref_incognito_mode))
                        setContentText(stringResource(MR.strings.notification_incognito_text))
                        setSmallIcon(R.drawable.ic_glasses_24dp)
                        setOngoing(true)

                        val pendingIntent = PendingIntent.getBroadcast(
                            this@App,
                            0,
                            Intent(ACTION_DISABLE_INCOGNITO_MODE).setPackage(packageName),
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        setContentIntent(pendingIntent)
                    }
                } else {
                    disableIncognitoReceiver.unregister()
                    cancelNotification(Notifications.ID_INCOGNITO_MODE)
                }
            }
            .launchIn(scope)

        privacyPreferences.analytics()
            .changes()
            .onEach(TelemetryConfig::setAnalyticsEnabled)
            .launchIn(scope)

        privacyPreferences.crashlytics()
            .changes()
            .onEach(TelemetryConfig::setCrashlyticsEnabled)
            .launchIn(scope)

        basePreferences.hardwareBitmapThreshold().changes()
            .onEach { ImageUtil.hardwareBitmapThreshold = it }
            .launchIn(scope)

        // Compute the device's maximum GPU texture size on an IO thread rather than the main
        // thread.  GLUtil.DEVICE_TEXTURE_LIMIT performs EGL driver queries that can take
        // hundreds of milliseconds on some devices.
        //
        // We use preference.get() (a suspend function that awaits the first DataStore emission)
        // instead of preference.isSet() (which reads from the in-memory snapshot).  The snapshot
        // starts as emptyPreferences() on every cold start and is populated asynchronously, so
        // isSet() always returns false before the first emission — meaning the EGL query and a
        // spurious write would occur on every launch, overwriting any value the user previously
        // configured.  Comparing the awaited value against the compiled-in default
        // (GLUtil.SAFE_TEXTURE_LIMIT) is the correct way to detect "never been set".
        scope.launch(Dispatchers.IO) {
            val preference = get<BasePreferences>().hardwareBitmapThreshold()
            if (preference.get() == preference.defaultValue()) {
                preference.set(GLUtil.DEVICE_TEXTURE_LIMIT)
            }
        }

        setAppCompatDelegateThemeMode(
            try {
                get<UiPreferences>().themeMode().getSync()
            } catch (e: Exception) {
                // DataStore snapshot may not have emitted yet on the first coroutine frame.
                // Fall back to the system default; the theme will be corrected once the
                // Flow emits (see MainActivity's collectAsState-based theming).
                logcat(LogPriority.WARN, e) { "Failed to read theme mode; defaulting to SYSTEM" }
                ThemeMode.SYSTEM
            },
        )

        // Updates widget update
        WidgetManager(get(), get()).apply { init(scope) }

        // LogcatLogger was installed at the top of onCreate() with a base priority.
        // Upgrade to VERBOSE now if the user has enabled verbose logging — Koin is ready
        // so networkPreferences is available.  We replace element [0] in a single list
        // operation (which is atomic for a single element set) rather than clear()+add(),
        // which would create a brief empty-list window where concurrent log calls could
        // be dropped, or add()-alongside, which would cause duplicate messages.
        try {
            if (networkPreferences.verboseLogging().getSync()) {
                LogcatLogger.loggers[0] = AndroidLogcatLogger(LogPriority.VERBOSE)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to read verboseLogging; keeping default log priority" }
        }

        initializeMigrator()
    }

    private fun initializeMigrator() {
        val preferenceStore = get<PreferenceStore>()
        val preference = preferenceStore.getInt(Preference.appStateKey("last_version_code"), 0)
        // Launch on IO so we await the first DataStore emission instead of reading a
        // potentially empty in-memory snapshot, which could misidentify an upgrade as a
        // fresh install and run only isAlways migrations instead of the versioned ones.
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Apply a bounded timeout so that a stuck or slow DataStore (e.g. locked
                // storage) cannot keep initGate pending forever.  Without this,
                // Migrator.awaitAndRelease() in MainActivity would wait for the full
                // MIGRATION_TIMEOUT_MS (30 s) before the UI renders, leaving users on a
                // blank screen.  If the read times out we default to 0 (fresh-install path)
                // which runs only Migration.ALWAYS migrations — the safest conservative
                // fallback.
                val old = withTimeoutOrNull(PREFERENCE_READ_TIMEOUT_MS) { preference.get() }
                    ?: run {
                        logcat(LogPriority.WARN) {
                            "DataStore read timed out after ${PREFERENCE_READ_TIMEOUT_MS}ms; " +
                                "treating as fresh install (old=0)"
                        }
                        0
                    }
                logcat { "Migration from $old to ${BuildConfig.VERSION_CODE}" }
                StartupTracker.complete(StartupTracker.Phase.MIGRATOR_STARTED)
                Migrator.initialize(
                    old = old,
                    new = BuildConfig.VERSION_CODE,
                    migrations = migrations,
                    onMigrationComplete = {
                        logcat { "Updating last version to ${BuildConfig.VERSION_CODE}" }
                        preference.set(BuildConfig.VERSION_CODE)
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Record the failure on MIGRATOR_STARTED so the diagnostic overlay surfaces
                // it, then mark the phase complete so the overlay does not show it as pending.
                // Calling Migrator.initialize with old=0 ensures initGate is completed and
                // MainActivity's awaitAndRelease() is not blocked indefinitely.
                StartupTracker.recordError(StartupTracker.Phase.MIGRATOR_STARTED, e)
                StartupTracker.complete(StartupTracker.Phase.MIGRATOR_STARTED)
                Migrator.initialize(
                    old = 0,
                    new = BuildConfig.VERSION_CODE,
                    migrations = migrations,
                    onMigrationComplete = {},
                )
            }
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(this).apply {
            val callFactoryLazy = lazy { get<NetworkHelper>().client }
            components {
                // NetworkFetcher.Factory
                add(OkHttpNetworkFetcherFactory(callFactoryLazy::value))
                // Decoder.Factory
                add(TachiyomiImageDecoder.Factory())
                // Fetcher.Factory
                add(BufferedSourceFetcher.Factory())
                add(get<MangaCoverFetcher.MangaCoverFactory>())
                add(get<MangaCoverFetcher.MangaFactory>())
                // Keyer
                add(get<MangaCoverKeyer>())
                add(get<MangaKeyer>())
            }

            memoryCache(
                MemoryCache.Builder()
                    .maxSizePercent(context)
                    .build(),
            )

            crossfade(300)
            val lowRam = DeviceUtil.isLowRamDevice(this@App)
            allowRgb565(lowRam)
            // On capable devices, request GPU-resident hardware bitmaps as the global default.
            // This eliminates the CPU→GPU upload on every render frame for covers and browse
            // images. getBitmapOrNull() handles the soft-copy needed for compress/notifications.
            if (!lowRam) bitmapConfig(Bitmap.Config.HARDWARE)
            if (networkPreferences.verboseLogging().getSync()) logger(DebugLogger())

            // Coil spawns a new thread for every image load by default
            fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
        }
            .build()
    }

    override fun onStart(owner: LifecycleOwner) {
        SecureActivityDelegateState.onApplicationStart(get())
    }

    override fun onStop(owner: LifecycleOwner) {
        SecureActivityDelegateState.onApplicationStopped(get())
    }

    /**
     * Called by the system when it determines that memory is running low. Applies progressive
     * trimming of the Coil image memory cache so the system can reclaim RAM:
     * - `TRIM_MEMORY_RUNNING_LOW` (foreground, system low): trim to 50% capacity
     * - `TRIM_MEMORY_UI_HIDDEN` and above (app backgrounded or critical): clear entirely
     *
     * The gradual approach keeps the cache warm during normal reading while still shedding
     * weight in long sessions where memory pressure builds up.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val cache = SingletonImageLoader.get(this).memoryCache ?: return
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            cache.clear()
        } else if (level >= TRIM_MEMORY_RUNNING_LOW) {
            cache.trimToSize(cache.maxSize / 2)
        }
    }

    override fun getPackageName(): String {
        try {
            // Override the value passed as X-Requested-With in WebView requests
            val stackTrace = Looper.getMainLooper().thread.stackTrace
            val isChromiumCall = stackTrace.any { trace ->
                trace.className.lowercase() in setOf("org.chromium.base.buildinfo", "org.chromium.base.apkinfo") &&
                    trace.methodName.lowercase() in setOf("getall", "getpackagename", "<init>")
            }

            if (isChromiumCall) return WebViewUtil.spoofedPackageName(applicationContext)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to inspect stack trace for Chromium spoofing" }
        }

        return super.getPackageName()
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to modify notification channels" }
        }
    }

    private inner class DisableIncognitoReceiver : BroadcastReceiver() {
        private var registered = false

        override fun onReceive(context: Context, intent: Intent) {
            basePreferences.incognitoMode().set(false)
        }

        fun register() {
            if (!registered) {
                val filter = IntentFilter(ACTION_DISABLE_INCOGNITO_MODE)
                ContextCompat.registerReceiver(this@App, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                unregisterReceiver(this)
                registered = false
            }
        }
    }

    companion object {
        private val ACTION_DISABLE_INCOGNITO_MODE = "${BuildConfig.APPLICATION_ID}.DISABLE_INCOGNITO_MODE"

        /**
         * Maximum time to wait for the DataStore preference read in [initializeMigrator].
         * A healthy device typically completes in < 500 ms; 5 s is generous while still
         * preventing an indefinite hang that would leave the user on a blank screen for the
         * full MIGRATION_TIMEOUT_MS (30 s) in MainActivity.
         */
        private const val PREFERENCE_READ_TIMEOUT_MS = 5_000L
    }
}
