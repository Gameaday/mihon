package eu.kanade.tachiyomi.util.system

import com.google.android.material.color.DynamicColors

val DeviceUtil.isDynamicColorAvailable by lazy {
    DynamicColors.isDynamicColorAvailable() || DeviceUtil.isSamsung
}
