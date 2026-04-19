@file:Suppress("UNUSED")

package ephyra.data.backup.models

/**
 * Backward-compatibility re-exports of backup model types.
 *
 * All types have been moved to [ephyra.domain.backup.model].
 * This file keeps the old package name resolvable so that data-layer callers
 * (backup creators, restorers, etc.) can be migrated incrementally without a
 * single large import churn across the whole `:data` module.
 *
 * New code should import directly from [ephyra.domain.backup.model].
 */

typealias Backup = ephyra.domain.backup.model.Backup
typealias BackupCategory = ephyra.domain.backup.model.BackupCategory
typealias BackupChapter = ephyra.domain.backup.model.BackupChapter
typealias BackupExtensionRepos = ephyra.domain.backup.model.BackupExtensionRepos
typealias BackupHistory = ephyra.domain.backup.model.BackupHistory
typealias BackupManga = ephyra.domain.backup.model.BackupManga
typealias BackupPreference = ephyra.domain.backup.model.BackupPreference
typealias BackupSourcePreferences = ephyra.domain.backup.model.BackupSourcePreferences
typealias BackupSource = ephyra.domain.backup.model.BackupSource
typealias BackupTracking = ephyra.domain.backup.model.BackupTracking
typealias PreferenceValue = ephyra.domain.backup.model.PreferenceValue
typealias IntPreferenceValue = ephyra.domain.backup.model.IntPreferenceValue
typealias LongPreferenceValue = ephyra.domain.backup.model.LongPreferenceValue
typealias FloatPreferenceValue = ephyra.domain.backup.model.FloatPreferenceValue
typealias StringPreferenceValue = ephyra.domain.backup.model.StringPreferenceValue
typealias BooleanPreferenceValue = ephyra.domain.backup.model.BooleanPreferenceValue
typealias StringSetPreferenceValue = ephyra.domain.backup.model.StringSetPreferenceValue

// Mapper function re-exports
val backupCategoryMapper = ephyra.domain.backup.model.backupCategoryMapper
val backupExtensionReposMapper = ephyra.domain.backup.model.backupExtensionReposMapper
val backupChapterMapper = ephyra.domain.backup.model.backupChapterMapper
val backupTrackMapper = ephyra.domain.backup.model.backupTrackMapper
