package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.domain.manga.model.SourceStatus
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun DownloadsBadge(count: Long) {
    if (count > 0) {
        Badge(
            text = "$count",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun UnreadBadge(count: Long) {
    if (count > 0) {
        Badge(text = "$count")
    }
}

@Composable
internal fun LanguageBadge(
    isLocal: Boolean,
    sourceLanguage: String,
) {
    if (isLocal) {
        Badge(
            imageVector = Icons.Outlined.Folder,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else if (sourceLanguage.isNotEmpty()) {
        Badge(
            text = sourceLanguage.uppercase(),
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun SourceHealthBadge(sourceStatus: Int) {
    val status = SourceStatus.fromValue(sourceStatus)
    when (status) {
        SourceStatus.DEAD -> Badge(
            imageVector = Icons.Outlined.Warning,
            color = MaterialTheme.colorScheme.error,
            iconColor = MaterialTheme.colorScheme.onError,
            contentDescription = stringResource(MR.strings.source_health_warning_dead),
        )
        SourceStatus.DEGRADED -> Badge(
            imageVector = Icons.Outlined.Warning,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
            contentDescription = stringResource(MR.strings.source_health_warning_degraded),
        )
        else -> {}
    }
}

@Composable
internal fun AuthorityBadge(hasCanonicalId: Boolean, canonicalId: String? = null) {
    if (hasCanonicalId) {
        val isJellyfin = canonicalId?.startsWith(JELLYFIN_CANONICAL_PREFIX) == true
        Badge(
            imageVector = Icons.Outlined.Verified,
            color = if (isJellyfin) JellyfinBadgeColor else MaterialTheme.colorScheme.primary,
            iconColor = if (isJellyfin) Color.White else MaterialTheme.colorScheme.onPrimary,
            contentDescription = stringResource(MR.strings.authority_badge_description),
        )
    }
}

/** Canonical ID prefix for Jellyfin authority tracker. */
private const val JELLYFIN_CANONICAL_PREFIX = "jf:"

/** Jellyfin brand color (#00A4DC). */
private val JellyfinBadgeColor = Color(0xFF00A4DC)

@PreviewLightDark
@Composable
private fun BadgePreview() {
    TachiyomiPreviewTheme {
        Column {
            DownloadsBadge(count = 10)
            UnreadBadge(count = 10)
            LanguageBadge(isLocal = true, sourceLanguage = "EN")
            LanguageBadge(isLocal = false, sourceLanguage = "EN")
            SourceHealthBadge(sourceStatus = SourceStatus.DEAD.value)
            SourceHealthBadge(sourceStatus = SourceStatus.DEGRADED.value)
            AuthorityBadge(hasCanonicalId = true)
        }
    }
}
