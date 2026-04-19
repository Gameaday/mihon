package ephyra.presentation.core.util.manga

import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.hasCustomCover
import ephyra.domain.manga.model.toSManga
import ephyra.domain.manga.service.CoverCache
import ephyra.source.local.image.LocalCoverManager
import ephyra.source.local.isLocal
import eu.kanade.tachiyomi.source.model.SManga
import java.io.InputStream
import java.time.Instant

/**
 * Call before updating [Manga.thumbnail_url] to ensure old cover can be cleared from cache
 */
fun Manga.prepUpdateCover(coverCache: CoverCache, remoteManga: SManga, refreshSameUrl: Boolean): Manga {
    // Never refresh covers if the new url is null, as the current url has possibly become invalid
    val newUrl = remoteManga.thumbnail_url ?: return this

    // Never refresh covers if the url is empty to avoid "losing" existing covers
    if (newUrl.isEmpty()) return this

    if (!refreshSameUrl && thumbnailUrl == newUrl) return this

    return when {
        isLocal() -> {
            this.copy(coverLastModified = Instant.now().toEpochMilli())
        }
        hasCustomCover(coverCache) -> {
            coverCache.deleteFromCache(this, false)
            this
        }
        else -> {
            coverCache.deleteFromCache(this, false)
            this.copy(coverLastModified = Instant.now().toEpochMilli())
        }
    }
}

fun Manga.removeCovers(coverCache: CoverCache): Manga {
    if (isLocal()) return this
    return if (coverCache.deleteFromCache(this, true) > 0) {
        return copy(coverLastModified = Instant.now().toEpochMilli())
    } else {
        this
    }
}

suspend fun Manga.editCover(
    coverManager: LocalCoverManager,
    stream: InputStream,
    updateManga: UpdateManga,
    coverCache: CoverCache,
) {
    if (isLocal()) {
        coverManager.update(toSManga(), stream)
        updateManga.awaitUpdateCoverLastModified(id)
    } else if (favorite) {
        coverCache.setCustomCoverToCache(this, stream)
        updateManga.awaitUpdateCoverLastModified(id)
    }
}
