package ephyra.presentation.core.ui

import cafe.adriel.voyager.core.screen.Screen

/**
 * Factory for creating an extension-repos screen without introducing a circular dependency.
 *
 * [feature:browse] cannot import [ExtensionReposScreen] directly because it lives in
 * [feature:settings], and [feature:settings] transitively depends on [feature:browse].
 * Koin-inject this factory instead; [feature:settings] provides the binding.
 *
 * Usage:
 * ```kotlin
 * val extensionReposFactory = koinInject<ExtensionReposScreenFactory>()
 * navigator.push(extensionReposFactory.create(null))
 * ```
 */
fun interface ExtensionReposScreenFactory {
    fun create(url: String?): Screen
}
