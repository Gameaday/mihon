package ephyra.presentation.core.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ephyra.domain.source.service.SourceManager
import org.koin.compose.koinInject

@Composable
fun ifSourcesLoaded(): Boolean {
    val sourceManager = koinInject<SourceManager>()
    return sourceManager.isInitialized.collectAsStateWithLifecycle().value
}
