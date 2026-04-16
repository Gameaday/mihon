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
import ephyra.app.crash.CrashActivity
import ephyra.app.crash.GlobalExceptionHandler
import ephyra.app.di.koinAppModule
import ephyra.app.di.koinAppModule_UI
import ephyra.app.di.koinPreferenceModule
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
import ephyra.domain.ui.model.setAppCompatDelegateThemeMode
import ephyra.i18n.MR
import ephyra.presentation.core.data.coil.TachiyomiImageDecoder
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate
import ephyra.presentation.core.ui.delegate.SecureActivityDelegateState
import ephyra.presentation.widget.WidgetManager
import ephyra.telemetry.TelemetryConfig
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
        get() = Configuration.Builder()
            .setWorkerFactory(get<WorkerFactory>())
            .build()

    @SuppressLint("LaunchActivityFromNotification")
    override fun onCreate() {
        super<Application>.onCreate()
        TelemetryConfig.init(applicationContext)

        GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)

        // Avoid potential crashes from multiple WebView processes
        val process = getProcessName()
        if (packageName != process) WebView.setDataDirectorySuffix(process)

        startKoin {
            androidContext(this@App)
            workManagerFactory()
            modules(koinAppModule, koinDomainModule, koinPreferenceModule, koinAppModule_UI)
        }

        setupNotificationChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val scope = ProcessLifecycleOwner.get().lifecycleScope

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

        basePreferences.hardwareBitmapThreshold().let { preference ->
            if (!preference.isSet()) preference.set(GLUtil.DEVICE_TEXTURE_LIMIT)
        }

        basePreferences.hardwareBitmapThreshold().changes()
            .onEach { ImageUtil.hardwareBitmapThreshold = it }
            .launchIn(scope)

        setAppCompatDelegateThemeMode(get<UiPreferences>().themeMode().getSync())

        // Updates widget update
        WidgetManager(get(), get()).apply { init(scope) }

        if (!LogcatLogger.isInstalled) {
            val minLogPriority = when {
                networkPreferences.verboseLogging().getSync() -> LogPriority.VERBOSE
                BuildConfig.DEBUG -> LogPriority.DEBUG
                else -> LogPriority.INFO
            }
            LogcatLogger.install()
            LogcatLogger.loggers += AndroidLogcatLogger(minLogPriority)
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
            val old = preference.get()
            logcat { "Migration from $old to ${BuildConfig.VERSION_CODE}" }
            Migrator.initialize(
                old = old,
                new = BuildConfig.VERSION_CODE,
                migrations = migrations,
                onMigrationComplete = {
                    logcat { "Updating last version to ${BuildConfig.VERSION_CODE}" }
                    preference.set(BuildConfig.VERSION_CODE)
                },
            )
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
        } catch (_: Exception) {
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
        private const val ACTION_DISABLE_INCOGNITO_MODE = "tachi.action.DISABLE_INCOGNITO_MODE"
    }
}
