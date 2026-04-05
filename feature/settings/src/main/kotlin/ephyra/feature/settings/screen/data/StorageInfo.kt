package ephyra.feature.settings.screen.data

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ephyra.core.common.util.storage.DiskUtil
import ephyra.i18n.MR
import ephyra.presentation.core.components.material.padding
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.theme.header
import ephyra.presentation.core.util.secondaryItemAlpha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun StorageInfo(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val storages = remember { DiskUtil.getExternalStorages(context) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        storages.forEach {
            StorageInfo(it)
        }
    }
}

@Composable
private fun StorageInfo(
    file: File,
) {
    val context = LocalContext.current

    var available by remember(file) { mutableStateOf(-1L) }
    var total by remember(file) { mutableStateOf(-1L) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            available = DiskUtil.getAvailableStorageSpace(file)
            total = DiskUtil.getTotalStorageSpace(file)
        }
    }

    val availableText = if (available == -1L) {
        stringResource(MR.strings.calculating)
    } else {
        Formatter.formatFileSize(context, available)
    }
    val totalText = if (total == -1L) {
        stringResource(MR.strings.calculating)
    } else {
        Formatter.formatFileSize(context, total)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = file.absolutePath,
            style = MaterialTheme.typography.header,
        )

        if (total > 0) {
            LinearProgressIndicator(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .fillMaxWidth()
                    .height(12.dp),
                progress = { (1 - (available / total.toFloat())) },
            )
        }

        Text(
            text = stringResource(MR.strings.available_disk_space_info, availableText, totalText),
            modifier = Modifier.secondaryItemAlpha(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
