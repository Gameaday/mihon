package ephyra.feature.browse.migration.sources

sealed interface MigrateSourceScreenEvent {
    data object ToggleSortingMode : MigrateSourceScreenEvent
    data object ToggleSortingDirection : MigrateSourceScreenEvent
}
