package ephyra.presentation.core.ui

import cafe.adriel.voyager.core.screen.Screen

/**
 * Factory for creating a migration-config screen without introducing a circular dependency.
 *
 * [feature:manga] cannot import [MigrationConfigScreen] directly because it lives in
 * `:app`. Koin-inject this factory instead; `:app` provides the binding.
 *
 * Usage:
 * ```kotlin
 * val migrationConfigFactory = koinInject<MigrationConfigScreenFactory>()
 * navigator.push(migrationConfigFactory.create(mangaId))
 * ```
 */
fun interface MigrationConfigScreenFactory {
    fun create(mangaId: Long): Screen
}
