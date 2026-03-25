package ephyra.core.migration.migrations

import ephyra.core.migration.Migration
import ephyra.core.migration.MigrationContext
import ephyra.core.common.util.lang.withIOContext
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences

class CategoryPreferencesCleanupMigration : Migration {
    override val version: Float = 10f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return@withIOContext false
        val downloadPreferences = migrationContext.get<DownloadPreferences>() ?: return@withIOContext false

        val getCategories = migrationContext.get<GetCategories>() ?: return@withIOContext false
        val allCategories = getCategories.await().mapTo(HashSet()) { it.id.toString() }

        val defaultCategory = libraryPreferences.defaultCategory().get()
        if (defaultCategory.toString() !in allCategories) {
            libraryPreferences.defaultCategory().delete()
        }

        val categoryPreferences = listOf(
            libraryPreferences.updateCategories(),
            libraryPreferences.updateCategoriesExclude(),
            downloadPreferences.removeExcludeCategories(),
            downloadPreferences.downloadNewChapterCategories(),
            downloadPreferences.downloadNewChapterCategoriesExclude(),
        )
        categoryPreferences.forEach { preference ->
            val ids = preference.get()
            val garbageIds = ids.minus(allCategories)
            if (garbageIds.isEmpty()) return@forEach
            preference.set(ids.minus(garbageIds))
        }
        return@withIOContext true
    }
}
