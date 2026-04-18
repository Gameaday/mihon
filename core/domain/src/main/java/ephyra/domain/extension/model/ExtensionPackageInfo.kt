package ephyra.domain.extension.model

/**
 * Pure-Kotlin representation of the Android-platform [android.content.pm.PackageInfo] fields
 * required by [ephyra.domain.extension.interactor.TrustExtension].
 *
 * Call sites in `:app` convert from [android.content.pm.PackageInfo] using
 * [android.content.pm.PackageInfo.packageName] and
 * [androidx.core.content.pm.PackageInfoCompat.getLongVersionCode].
 *
 * This type lives in domain so that [TrustExtension] has no dependency on Android SDK types,
 * satisfying Principle 1 (The Dependency Rule Is Absolute).
 */
data class ExtensionPackageInfo(
    val packageName: String,
    val versionCode: Long,
)
