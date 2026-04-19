package ephyra.app.startup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Full-screen diagnostic overlay that becomes visible when app startup takes longer than
 * [TIMEOUT_MS].  It lists every [StartupTracker.Phase] with a visual status indicator so
 * that the user (or a tester) can take a screenshot and report exactly which phase is
 * blocking startup.
 *
 * The overlay is intentionally unthemed (hardcoded dark background / light text) so it
 * is visible regardless of which theme is active or whether the theme itself is broken.
 *
 * This composable is a no-op in release builds ([isReleaseBuild] = true).
 */
@Composable
fun StartupDiagnosticOverlay(
    isReleaseBuild: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isReleaseBuild) return

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(TIMEOUT_MS)
        if (!StartupTracker.isComplete(StartupTracker.Phase.HOME_SCREEN_LOADED)) {
            visible = true
            // Poll every 500ms; hide the overlay once the app does become ready.
            while (!StartupTracker.isComplete(StartupTracker.Phase.HOME_SCREEN_LOADED)) {
                delay(500)
            }
            visible = false
        }
    }

    AnimatedVisibility(visible = visible, enter = fadeIn()) {
        DiagnosticContent(modifier = modifier)
    }
}

@Composable
private fun DiagnosticContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE6000000)), // 90% opaque black
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header
            Text(
                text = "Startup Diagnostic",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "App not ready after ${TIMEOUT_MS / 1_000}s " +
                    "(${StartupTracker.elapsedMs()}ms elapsed)",
                color = Color(0xFFAAAAAA),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF444444))
            Spacer(Modifier.height(4.dp))

            // Phase list — polls every 500ms while visible so newly completed phases appear.
            val completed by produceState(initialValue = StartupTracker.completedPhases) {
                while (true) {
                    delay(500)
                    value = StartupTracker.completedPhases
                }
            }
            // Track elapsed time for the overdue indicator (polls every 500ms alongside phase state).
            val nowElapsedMs by produceState(initialValue = StartupTracker.elapsedMs()) {
                while (true) {
                    delay(500)
                    value = StartupTracker.elapsedMs()
                }
            }
            StartupTracker.Phase.entries.forEach { phase ->
                val entry = completed.firstOrNull { it.phase == phase }
                PhaseRow(
                    phase = phase,
                    elapsedMs = entry?.let { it.timestampMs - StartupTracker.processStartMs },
                    nowElapsedMs = nowElapsedMs,
                )
            }

            // Error detail (if any)
            val error = StartupTracker.lastError
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF444444))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Last error",
                    color = Color(0xFFFF6B6B),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = error.stackTraceToString().take(600),
                    color = Color(0xFFFFAAAA),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Please screenshot this screen and include it in your bug report.",
                color = Color(0xFFAAAAAA),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PhaseRow(phase: StartupTracker.Phase, elapsedMs: Long?, nowElapsedMs: Long) {
    val isComplete = elapsedMs != null
    val isOverdue = !isComplete && nowElapsedMs > phase.timeoutMs && StartupTracker.lastError == null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = when {
                isComplete -> Icons.Filled.CheckCircle
                StartupTracker.lastError != null -> Icons.Outlined.Error
                isOverdue -> Icons.Outlined.Warning
                else -> Icons.Outlined.HourglassEmpty
            },
            contentDescription = null,
            tint = when {
                isComplete -> Color(0xFF66BB6A)
                StartupTracker.lastError != null -> Color(0xFFFF6B6B)
                isOverdue -> Color(0xFFFF9800) // amber — overdue but no error yet
                else -> Color(0xFFFFB74D)
            },
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = phase.displayName,
            color = when {
                isComplete -> Color.White
                isOverdue -> Color(0xFFFFCC80) // amber text for overdue
                else -> Color(0xFF888888)
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        when {
            isComplete -> Text(
                text = "+${elapsedMs}ms",
                color = Color(0xFF666666),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            )
            isOverdue -> Text(
                text = "OVERDUE (>${phase.timeoutMs / 1_000}s)",
                color = Color(0xFFFF9800),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

private const val TIMEOUT_MS = 10_000L
