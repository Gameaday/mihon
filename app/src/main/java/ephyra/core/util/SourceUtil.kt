package ephyra.core.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import ephyra.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ifSourcesLoaded(): Boolean {
    return remember { Injekt.get<SourceManager>().isInitialized }.collectAsStateWithLifecycle().value
}
