package ephyra.feature.library.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import ephyra.domain.manga.model.SourceStatus
import ephyra.i18n.MR
import ephyra.presentation.core.components.Badge
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.theme.TachiyomiPreviewTheme

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
        val brandColor = authorityBrandColor(canonicalId)
        Badge(
            imageVector = Icons.Outlined.Verified,
            color = brandColor ?: MaterialTheme.colorScheme.primary,
            iconColor = if (brandColor != null) Color.White else MaterialTheme.colorScheme.onPrimary,
            contentDescription = stringResource(MR.strings.authority_badge_description),
        )
    }
}

/**
 * Returns the brand color for a known authority, or null for unknown prefixes.
 * Used by both library badges and the series detail page authority badge.
 */
internal fun authorityBrandColor(canonicalId: String?): Color? {
    if (canonicalId == null) return null
    val prefix = canonicalId.substringBefore(":", "")
    return AUTHORITY_BRAND_COLORS[prefix]
}

/**
 * Returns a gradient [Brush] for authorities that use a multi-color brand identity,
 * or null when the authority uses a single solid color. Currently only Jellyfin
 * has a gradient (purple → blue, matching the Jellyfin logo).
 */
internal fun authorityBrandGradient(canonicalId: String?): Brush? {
    if (canonicalId == null) return null
    val prefix = canonicalId.substringBefore(":", "")
    val colors = AUTHORITY_GRADIENT_COLORS[prefix] ?: return null
    return Brush.horizontalGradient(colors)
}

/** Brand colors for known authority trackers. */
private val AUTHORITY_BRAND_COLORS = mapOf(
    "al" to Color(0xFF02A9FF), // AniList blue
    "mal" to Color(0xFF2E51A2), // MyAnimeList blue
    "mu" to Color(0xFFFF6740), // MangaUpdates orange
    "jf" to Color(0xFF00A4DC), // Jellyfin blue (primary)
)

/** Gradient stops for authorities with multi-color brand identities. */
private val AUTHORITY_GRADIENT_COLORS = mapOf(
    "jf" to listOf(Color(0xFFAA5CC3), Color(0xFF00A4DC)), // Jellyfin purple → blue
)

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
