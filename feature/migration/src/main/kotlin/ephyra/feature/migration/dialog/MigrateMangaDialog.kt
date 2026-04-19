package ephyra.feature.migration.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import dev.icerock.moko.resources.StringResource
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.withUIContext
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.hasCustomCover
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.migration.models.MigrationFlag
import ephyra.domain.migration.usecases.MigrateMangaUseCase
import ephyra.domain.source.service.SourcePreferences
import ephyra.i18n.MR
import ephyra.presentation.core.components.LabeledCheckbox
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.screens.LoadingScreen
import kotlinx.coroutines.flow.update
import kotlin.collections.toMutableSet

private fun MigrationFlag.getLabel(): StringResource {
    return when (this) {
        MigrationFlag.CHAPTER -> MR.strings.chapters
        MigrationFlag.CATEGORY -> MR.strings.categories
        MigrationFlag.CUSTOM_COVER -> MR.strings.custom_cover
        MigrationFlag.NOTES -> MR.strings.action_notes
        MigrationFlag.REMOVE_DOWNLOAD -> MR.strings.delete_downloaded
    }
}

@Composable
fun Screen.MigrateMangaDialog(
    current: Manga,
    target: Manga,
    onClickTitle: () -> Unit,
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit = onDismissRequest,
) {
    val scope = rememberCoroutineScope()

    val screenModel = koinScreenModel<MigrateDialogScreenModel>()
    LaunchedEffect(current, target) {
        screenModel.init(current, target)
    }
    val state by screenModel.state.collectAsStateWithLifecycle()

    if (state.isMigrated) return

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                state.applicableFlags.fastForEach { flag ->
                    LabeledCheckbox(
                        label = stringResource(flag.getLabel()),
                        checked = flag in state.selectedFlags,
                        onCheckedChange = { screenModel.toggleSelection(flag) },
                    )
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onClickTitle()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_show_manga))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        scope.launchIO {
                            screenModel.migrateManga(replace = false)
                            withUIContext { onComplete() }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.copy))
                }
                TextButton(
                    onClick = {
                        scope.launchIO {
                            screenModel.migrateManga(replace = true)
                            withUIContext { onComplete() }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.migrate))
                }
            }
        },
    )
}

class MigrateDialogScreenModel(
    private val sourcePreference: SourcePreferences,
    private val coverCache: CoverCache,
    private val downloadManager: DownloadManager,
    private val migrateManga: MigrateMangaUseCase,
) : StateScreenModel<MigrateDialogScreenModel.State>(State()) {

    fun init(current: Manga, target: Manga) {
        val applicableFlags = buildList {
            MigrationFlag.entries.forEach {
                val applicable = when (it) {
                    MigrationFlag.CHAPTER -> true
                    MigrationFlag.CATEGORY -> true
                    MigrationFlag.CUSTOM_COVER -> current.hasCustomCover(coverCache)
                    MigrationFlag.NOTES -> current.notes.isNotBlank()
                    MigrationFlag.REMOVE_DOWNLOAD -> downloadManager.getDownloadCount(current) > 0
                }
                if (applicable) add(it)
            }
        }
        val selectedFlags = sourcePreference.migrationFlags().getSync()
        mutableState.update {
            State(
                current = current,
                target = target,
                applicableFlags = applicableFlags,
                selectedFlags = selectedFlags,
            )
        }
    }

    fun toggleSelection(flag: MigrationFlag) {
        mutableState.update {
            val selectedFlags = it.selectedFlags.toMutableSet()
                .apply { if (contains(flag)) remove(flag) else add(flag) }
                .toSet()
            it.copy(selectedFlags = selectedFlags)
        }
    }

    suspend fun migrateManga(replace: Boolean) {
        val state = state.value
        val current = state.current ?: return
        val target = state.target ?: return
        sourcePreference.migrationFlags().set(state.selectedFlags)
        mutableState.update { it.copy(isMigrating = true) }
        migrateManga(current, target, replace)
        mutableState.update { it.copy(isMigrating = false, isMigrated = true) }
    }

    data class State(
        val current: Manga? = null,
        val target: Manga? = null,
        val applicableFlags: List<MigrationFlag> = emptyList(),
        val selectedFlags: Set<MigrationFlag> = emptySet(),
        val isMigrating: Boolean = false,
        val isMigrated: Boolean = false,
    )
}
