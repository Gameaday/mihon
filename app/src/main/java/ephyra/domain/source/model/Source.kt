package ephyra.domain.source.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import ephyra.app.extension.ExtensionManager
import ephyra.domain.source.model.Source

fun Source.icon(extensionManager: ExtensionManager): ImageBitmap? {
    return extensionManager.getAppIconForSource(id)
        ?.toBitmap()
        ?.asImageBitmap()
}
