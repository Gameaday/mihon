package ephyra.feature.settings.screen.debug

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.core.common.util.lang.toDateTimestampString
import ephyra.core.common.util.system.workManager
import ephyra.domain.ui.UiPreferences
import ephyra.i18n.MR
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.AppBarActions
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.util.Screen
import ephyra.presentation.core.util.ioCoroutineScope
import ephyra.presentation.core.util.plus
import ephyra.presentation.core.util.system.copyToClipboard
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class WorkerInfoScreen : Screen() {

    companion object {
        const val TITLE = "Worker info"
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = koinScreenModel<Model>()
        val enqueued by screenModel.enqueued.collectAsState()
        val finished by screenModel.finished.collectAsState()
        val running by screenModel.running.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = TITLE,
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_copy_to_clipboard),
                                    icon = Icons.Default.ContentCopy,
                                    onClick = {
                                        context.copyToClipboard(TITLE, enqueued + finished + running)
                                    },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumn(
                contentPadding = contentPadding + PaddingValues(horizontal = 16.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                item { SectionTitle(title = "Enqueued") }
                item { SectionText(text = enqueued) }

                item { SectionTitle(title = "Finished") }
                item { SectionText(text = finished) }

                item { SectionTitle(title = "Running") }
                item { SectionText(text = running) }
            }
        }
    }

    @Composable
    private fun SectionTitle(title: String) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }

    @Composable
    private fun SectionText(text: String) {
        Text(
            text = text,
            softWrap = false,
            fontFamily = FontFamily.Monospace,
        )
    }

    class Model(
        context: Context,
        private val uiPreferences: UiPreferences,
    ) : ScreenModel {
        private val workManager = context.workManager

        val finished = workManager
            .getWorkInfosFlow(
                WorkQuery.fromStates(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED),
            )
            .map(::constructString)
            .stateIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), "")

        val running = workManager
            .getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.RUNNING))
            .map(::constructString)
            .stateIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), "")

        val enqueued = workManager
            .getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.ENQUEUED))
            .map(::constructString)
            .stateIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), "")

        private fun constructString(list: List<WorkInfo>) = buildString {
            if (list.isEmpty()) {
                appendLine("-")
            } else {
                list.fastForEach { workInfo ->
                    appendLine("Id: ${workInfo.id}")
                    appendLine("Tags:")
                    workInfo.tags.forEach {
                        appendLine(" - $it")
                    }
                    appendLine("State: ${workInfo.state}")
                    if (workInfo.state == WorkInfo.State.ENQUEUED) {
                        val timestamp = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(workInfo.nextScheduleTimeMillis),
                            ZoneId.systemDefault(),
                        )
                            .toDateTimestampString(
                                UiPreferences.dateFormat(
                                    kotlinx.coroutines.runBlocking { uiPreferences.dateFormat().get() },
                                ),
                            )
                        appendLine("Next scheduled run: $timestamp")
                        appendLine("Attempt #${workInfo.runAttemptCount + 1}")
                    }
                    appendLine()
                }
            }
        }
    }
}
