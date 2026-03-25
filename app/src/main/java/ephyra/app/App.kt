package ephyra.app

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
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
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.util.DebugLogger
import dev.ephyra.injekt.patchInjekt
import ephyra.domain.DomainModule
import ephyra.domain.base.BasePreferences
import ephyra.domain.ui.UiPreferences
import ephyra.domain.ui.model.setAppCompatDelegateThemeMode
import ephyra.app.core.security.PrivacyPreferences
import ephyra.app.crash.CrashActivity
import ephyra.app.crash.GlobalExceptionHandler
import ephyra.app.data.coil.BufferedSourceFetcher
import ephyra.app.data.coil.MangaCoverFetcher
import ephyra.app.data.coil.MangaCoverKeyer
import ephyra.app.data.coil.MangaKeyer
import ephyra.app.data.coil.TachiyomiImageDecoder
import ephyra.app.data.notification.Notifications
import ephyra.app.di.AppModule
import ephyra.app.di.PreferenceModule
import eu.kanade.ephyra.network.NetworkHelper
import eu.kanade.ephyra.network.NetworkPreferences
import ephyra.app.ui.base.delegate.SecureActivityDelegate
import ephyra.app.util.system.DeviceUtil
import ephyra.app.util.system.GLUtil
import ephyra.app.util.system.WebViewUtil
import ephyra.app.util.system.animatorDurationScale
import ephyra.app.util.system.cancelNotification
import ephyra.app.util.system.notify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import ephyra.core.migration.Migrator
import ephyra.core.migration.migrations.migrations
import ephyra.telemetry.TelemetryConfig
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import ephyra.app.di.koinAppModule
import ephyra.app.di.koinDomainModule
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.logcat
import ephyra.i18n.MR
import ephyra.presentation.widget.WidgetManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class App : Application(), DefaultLifecycleObserver, SingletonImageLoader.Factory {

    private val basePreferences: BasePreferences by injectLazy()
    private val privacyPreferences: PrivacyPreferences by injectLazy()
    private val networkPreferences: NetworkPreferences by injectLazy()

    private val disableIncognitoReceiver = DisableIncognitoReceiver()

    @SuppressLint("LaunchActivityFromNotification")
    override fun onCreate() {
        super<Application>.onCreate()
        patchInjekt()
        TelemetryConfig.init(applicationContext)

        GlobalExceptionHandler.initialize(applicationContext, CrashActivity::class.java)

        // Avoid potential crashes from multiple WebView processes
        val process = getProcessName()
        if (packageName != process) WebView.setDataDirectorySuffix(process)
        
        startKoin {
            androidContext(this@App)
            modules(koinAppModule, koinDomainModule)
        }

        Injekt.importModule(PreferenceModule(this))
        Injekt.importModule(AppModule(this))
        Injekt.importModule(DomainModule())

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
                            Intent(ACTION_DISABLE_INCOGNITO_MODE).setPackage(BuildConfig.APPLICATION_ID),
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

        setAppCompatDelegateThemeMode(Injekt.get<UiPreferences>().themeMode().get())

        // Updates widget update
        WidgetManager(Injekt.get(), Injekt.get()).apply { init(scope) }

        if (!LogcatLogger.isInstalled) {
            val minLogPriority = when {
                networkPreferences.verboseLogging().get() -> LogPriority.VERBOSE
                BuildConfig.DEBUG -> LogPriority.DEBUG
                else -> LogPriority.INFO
            }
            LogcatLogger.install()
            LogcatLogger.loggers += AndroidLogcatLogger(minLogPriority)
        }

        initializeMigrator()
    }

    private fun initializeMigrator() {
        val preferenceStore = Injekt.get<PreferenceStore>()
        val preference = preferenceStore.getInt(Preference.appStateKey("last_version_code"), 0)
        logcat { "Migration from ${preference.get()} to ${BuildConfig.VERSION_CODE}" }
        Migrator.initialize(
            old = preference.get(),
            new = BuildConfig.VERSION_CODE,
            migrations = migrations,
            onMigrationComplete = {
                logcat { "Updating last version to ${BuildConfig.VERSION_CODE}" }
                preference.set(BuildConfig.VERSION_CODE)
            },
        )
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(this).apply {
            val callFactoryLazy = lazy { Injekt.get<NetworkHelper>().client }
            components {
                // NetworkFetcher.Factory
                add(OkHttpNetworkFetcherFactory(callFactoryLazy::value))
                // Decoder.Factory
                add(TachiyomiImageDecoder.Factory())
                // Fetcher.Factory
                add(BufferedSourceFetcher.Factory())
                add(MangaCoverFetcher.MangaCoverFactory(callFactoryLazy))
                add(MangaCoverFetcher.MangaFactory(callFactoryLazy))
                // Keyer
                add(MangaCoverKeyer())
                add(MangaKeyer())
            }

            memoryCache(
                MemoryCache.Builder()
                    .maxSizePercent(context)
                    .build(),
            )

            crossfade((300 * this@App.animatorDurationScale).toInt())
            val lowRam = DeviceUtil.isLowRamDevice(this@App)
            allowRgb565(lowRam)
            // On capable devices, request GPU-resident hardware bitmaps as the global default.
            // This eliminates the CPU→GPU upload on every render frame for covers and browse
            // images. getBitmapOrNull() handles the soft-copy needed for compress/notifications.
            if (!lowRam) bitmapConfig(Bitmap.Config.HARDWARE)
            if (networkPreferences.verboseLogging().get()) logger(DebugLogger())

            // Coil spawns a new thread for every image load by default
            fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
        }
            .build()
    }

    override fun onStart(owner: LifecycleOwner) {
        SecureActivityDelegate.onApplicationStart()
    }

    override fun onStop(owner: LifecycleOwner) {
        SecureActivityDelegate.onApplicationStopped()
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
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            cache.clear()
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
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
                ContextCompat.registerReceiver(
                    this@App,
                    this,
                    IntentFilter(ACTION_DISABLE_INCOGNITO_MODE),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
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
