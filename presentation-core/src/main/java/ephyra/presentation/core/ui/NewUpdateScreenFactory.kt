package ephyra.presentation.core.ui

import cafe.adriel.voyager.core.screen.Screen

/**
 * Factory for creating a new-update screen without introducing a circular dependency.
 *
 * [feature:settings] cannot import [NewUpdateScreen] directly because it lives in
 * [feature:more], and [feature:more] transitively depends on [feature:settings].
 * Koin-inject this factory instead; [feature:more] (or [:app]) provides the binding.
 *
 * Usage:
 * ```kotlin
 * val newUpdateScreenFactory = koinInject<NewUpdateScreenFactory>()
 * navigator.push(newUpdateScreenFactory.create(versionName, changelog, releaseLink, downloadLink))
 * ```
 */
fun interface NewUpdateScreenFactory {
    fun create(
        versionName: String,
        changelogInfo: String,
        releaseLink: String,
        downloadLink: String,
    ): Screen
}
