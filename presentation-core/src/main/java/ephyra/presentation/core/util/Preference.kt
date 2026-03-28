package ephyra.presentation.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ephyra.core.common.preference.Preference

/**
 * Collects [Preference.changes] as Compose state, automatically pausing collection when the
 * host lifecycle drops below [androidx.lifecycle.Lifecycle.State.STARTED] (app backgrounded).
 *
 * This replaces the previous `collectAsState()` wrapper to prevent upstream SharedPreference
 * and database flows from staying active while the app is not visible, reducing background
 * battery draw (H2 healing fix).
 */
@Composable
fun <T> Preference<T>.collectAsState(): State<T> {
    val flow = remember(this) { changes() }
    return flow.collectAsStateWithLifecycle(initialValue = getSync())
}
