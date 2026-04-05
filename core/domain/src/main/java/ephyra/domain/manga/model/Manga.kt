package ephyra.domain.manga.model

import ephyra.core.common.preference.TriState
import ephyra.core.metadata.comicinfo.ComicInfo
import ephyra.core.metadata.comicinfo.ComicInfoPublishingStatus
import ephyra.domain.base.BasePreferences
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking

// TODO: move these into the domain model
val Manga.readingMode: Long
    get() = viewerFlags and ReadingMode.MASK.toLong()

val Manga.readerOrientation: Long
    get() = viewerFlags and ReaderOrientation.MASK.toLong()

fun Manga.downloadedFilter(basePreferences: BasePreferences): TriState {
    if (runBlocking { basePreferences.downloadedOnly().get() }) return TriState.ENABLED_IS
    return when (downloadedFilterRaw) {
        Manga.CHAPTER_SHOW_DOWNLOADED -> TriState.ENABLED_IS
        Manga.CHAPTER_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
        else -> TriState.DISABLED
    }
}

fun Manga.chaptersFiltered(basePreferences: BasePreferences): Boolean {
    return unreadFilter != TriState.DISABLED ||
        downloadedFilter(basePreferences) != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED
}

fun Manga.toSManga(): SManga = SManga.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre.orEmpty().joinToString()
    it.status = status.toInt()
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
}

fun Manga.copyFrom(other: SManga): Manga {
    val author = other.author ?: author
    val artist = other.artist ?: artist
    val description = other.description ?: description
    val genres = if (other.genre != null) {
        other.getGenres()
    } else {
        genre
    }
    val thumbnailUrl = other.thumbnail_url ?: thumbnailUrl
    return this.copy(
        author = author,
        artist = artist,
        description = description,
        genre = genres,
        thumbnailUrl = thumbnailUrl,
        status = other.status.toLong(),
        updateStrategy = other.update_strategy,
        initialized = other.initialized && initialized,
    )
}

fun Manga.hasCustomCover(coverCache: CoverCache): Boolean {
    return coverCache.getCustomCoverFile(this)?.exists() ?: false
}

/**
 * Creates a ComicInfo instance based on the manga and chapter metadata.
 */
fun getComicInfo(
    manga: Manga,
    chapter: Chapter,
    urls: List<String>,
    categories: List<String>?,
    sourceName: String,
    sourceLang: String? = null,
    startYear: Int = 0,
) = ComicInfo(
    title = ComicInfo.Title(chapter.name),
    series = ComicInfo.Series(manga.title),
    number = chapter.chapterNumber.takeIf { it >= 0 }?.let {
        if ((it.rem(1) == 0.0)) {
            ComicInfo.Number(it.toInt().toString())
        } else {
            ComicInfo.Number(it.toString())
        }
    },
    count = null,
    volume = null,
    web = ComicInfo.Web(urls.joinToString(" ")),
    summary = manga.description?.let { ComicInfo.Summary(it) },
    year = startYear.takeIf { it > 0 }?.let { ComicInfo.Year(it) },
    writer = manga.author?.let { ComicInfo.Writer(it) },
    penciller = manga.artist?.let { ComicInfo.Penciller(it) },
    translator = chapter.scanlator?.let { ComicInfo.Translator(it) },
    genre = manga.genre?.let { ComicInfo.Genre(it.joinToString()) },
    publishingStatus = ComicInfo.PublishingStatusTachiyomi(
        ComicInfoPublishingStatus.toComicInfoValue(manga.status),
    ),
    categories = categories?.let { ComicInfo.CategoriesTachiyomi(it.joinToString()) },
    source = ComicInfo.SourceEphyra(sourceName),
    languageISO = sourceLang?.takeIf { it.isNotBlank() && it != "all" }
        ?.let { ComicInfo.LanguageISO(it) },
    manga = determineMangaField(manga),
    inker = null,
    colorist = null,
    letterer = null,
    coverArtist = null,
    tags = null,
)

/**
 * Determines the ComicInfo Manga field value based on content type and reading mode.
 *
 * Values per ComicInfo v2.0 spec:
 * - "YesAndRightToLeft": manga with right-to-left reading order (default for manga)
 * - "Yes": manga/comic with left-to-right or vertical reading order
 * - null: not a comic/manga format (novels, books)
 */
private fun determineMangaField(manga: Manga): ComicInfo.Manga? {
    return when (manga.contentType) {
        ContentType.NOVEL, ContentType.BOOK -> null
        ContentType.MANGA, ContentType.UNKNOWN -> {
            val mode = ReadingMode.fromPreference(manga.readingMode.toInt())
            when (mode) {
                ReadingMode.LEFT_TO_RIGHT,
                ReadingMode.WEBTOON,
                ReadingMode.CONTINUOUS_VERTICAL,
                ReadingMode.VERTICAL,
                -> ComicInfo.Manga("Yes")
                // RIGHT_TO_LEFT and DEFAULT both get manga RTL
                else -> ComicInfo.Manga("YesAndRightToLeft")
            }
        }
    }
}
