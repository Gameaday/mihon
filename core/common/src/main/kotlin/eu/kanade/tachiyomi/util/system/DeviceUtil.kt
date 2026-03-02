package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

object DeviceUtil {

    val isMiui: Boolean by lazy {
        getSystemProperty("ro.miui.ui.version.name")?.isNotEmpty() ?: false
    }

    /**
     * Extracts the MIUI major version code from a string like "V12.5.3.0.QFGMIXM".
     *
     * @return MIUI major version code (e.g., 13) or null if can't be parsed.
     */
    val miuiMajorVersion: Int? by lazy {
        if (!isMiui) return@lazy null

        Build.VERSION.INCREMENTAL
            .substringBefore('.')
            .trimStart('V')
            .toIntOrNull()
    }

    @SuppressLint("PrivateApi")
    fun isMiuiOptimizationDisabled(): Boolean {
        val sysProp = getSystemProperty("persist.sys.miui_optimization")
        if (sysProp == "0" || sysProp == "false") {
            return true
        }

        return try {
            Class.forName("android.miui.AppOpsUtils")
                .getDeclaredMethod("isXOptMode")
                .invoke(null) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    val isSamsung: Boolean by lazy {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    val oneUiVersion: Double? by lazy {
        try {
            val semPlatformIntField = Build.VERSION::class.java.getDeclaredField("SEM_PLATFORM_INT")
            val version = semPlatformIntField.getInt(null) - 90000
            if (version < 0) {
                1.0
            } else {
                ((version / 10000).toString() + "." + version % 10000 / 100).toDouble()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * A list of package names that may be incorrectly resolved as usable browsers by
     * the system.
     *
     * If these are resolved for [android.content.Intent.ACTION_VIEW], it prevents the
     * system from opening a proper browser or any usable app .
     *
     * Some of them may only be present on certain manufacturer's devices.
     */
    val invalidDefaultBrowsers = setOf(
        "android",
        // Honor
        "com.hihonor.android.internal.app",
        // Huawei
        "com.huawei.android.internal.app",
        // Lenovo
        "com.zui.resolver",
        // Infinix
        "com.transsion.resolver",
        // Xiaomi Redmi
        "com.android.intentresolver",
    )

    /**
     * ActivityManager#isLowRamDevice is based on a system property, which isn't
     * necessarily trustworthy. 1GB is supposedly the regular threshold.
     *
     * Instead, we consider anything with less than 3GB of RAM as low memory
     * considering how heavy image processing can be.
     */
    fun isLowRamDevice(context: Context): Boolean {
        val memInfo = ActivityManager.MemoryInfo()
        context.getSystemService<ActivityManager>()!!.getMemoryInfo(memInfo)
        val totalMemBytes = memInfo.totalMem
        return totalMemBytes < 3L * 1024 * 1024 * 1024
    }

    /**
     * Broad device performance tiers used to scale reader preload windows,
     * concurrent download workers, and RecyclerView caches without user configuration.
     *
     * - [LOW]: Less than 2 GB of total RAM — conservative settings to avoid OOM.
     * - [MEDIUM]: 2–3.9 GB of total RAM — balanced defaults.
     * - [HIGH]: 4 GB or more total RAM — aggressive preloading and concurrency.
     */
    enum class PerformanceTier { LOW, MEDIUM, HIGH }

    // Cached so the ActivityManager query is only performed once per process.
    @Volatile private var cachedPerformanceTier: PerformanceTier? = null

    /**
     * Returns the [PerformanceTier] for the device by reading total physical RAM.
     * The result is cached after the first call. Thread-safe via the [DeviceUtil] object lock.
     */
    @Synchronized
    fun performanceTier(context: Context): PerformanceTier =
        cachedPerformanceTier ?: run {
            val memInfo = ActivityManager.MemoryInfo()
            context.getSystemService<ActivityManager>()!!.getMemoryInfo(memInfo)
            val totalGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val tier = when {
                totalGb < 2.0 -> PerformanceTier.LOW
                totalGb < 4.0 -> PerformanceTier.MEDIUM
                else -> PerformanceTier.HIGH
            }
            cachedPerformanceTier = tier
            tier
        }

    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String?): String? {
        return try {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Unable to use SystemProperties.get()" }
            null
        }
    }
}
