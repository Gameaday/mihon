package ephyra.presentation.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ephyra.i18n.MR
import ephyra.presentation.core.i18n.stringResource

val IndexingBannerBackgroundColor = Color(0xFF36B37E)
val DownloadedOnlyBannerBackgroundColor = Color(0xFF0065FF)
val IncognitoModeBannerBackgroundColor = Color(0xFF44546F)

@Composable
fun AppStateBanners(
    downloadedOnlyMode: Boolean,
    incognitoMode: Boolean,
    indexing: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (indexing) {
            IndexingBanner()
        }
        if (downloadedOnlyMode) {
            DownloadedOnlyModeBanner()
        }
        if (incognitoMode) {
            IncognitoModeBanner()
        }
    }
}

@Composable
private fun IndexingBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndexingBannerBackgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .padding(end = 16.dp)
                .size(16.dp),
            color = Color.White,
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(MR.strings.loading),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun DownloadedOnlyModeBanner() {
    Text(
        text = stringResource(MR.strings.label_downloaded_only),
        modifier = Modifier
            .fillMaxWidth()
            .background(DownloadedOnlyBannerBackgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
    )
}

@Composable
private fun IncognitoModeBanner() {
    Text(
        text = stringResource(MR.strings.pref_incognito_mode),
        modifier = Modifier
            .fillMaxWidth()
            .background(IncognitoModeBannerBackgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
    )
}
