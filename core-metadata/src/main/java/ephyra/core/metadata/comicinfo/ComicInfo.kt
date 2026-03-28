package ephyra.core.metadata.comicinfo

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

const val COMIC_INFO_FILE = "ComicInfo.xml"

fun SManga.getComicInfo() = ComicInfo(
    series = ComicInfo.Series(title),
    summary = description?.let { ComicInfo.Summary(it) },
    writer = author?.let { ComicInfo.Writer(it) },
    penciller = artist?.let { ComicInfo.Penciller(it) },
    genre = genre?.let { ComicInfo.Genre(it) },
    publishingStatus = ComicInfo.PublishingStatusTachiyomi(
        ComicInfoPublishingStatus.toComicInfoValue(status.toLong()),
    ),
    title = null,
    number = null,
    count = null,
    volume = null,
    year = null,
    web = null,
    translator = null,
    inker = null,
    colorist = null,
    letterer = null,
    coverArtist = null,
    tags = null,
    categories = null,
    source = null,
    languageISO = null,
    manga = null,
)

fun SManga.copyFromComicInfo(comicInfo: ComicInfo) {
    comicInfo.series?.let { title = it.value }
    comicInfo.writer?.let { author = it.value }
    comicInfo.summary?.let { description = it.value }

    // Merge ComicInfo genre/tags/categories with existing genres (additive, not replacing)
    val comicInfoGenres = listOfNotNull(
        comicInfo.genre?.value,
        comicInfo.tags?.value,
        comicInfo.categories?.value,
    )
        .flatMap { it.split(",") }
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (comicInfoGenres.isNotEmpty()) {
        val existing = genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val merged = (existing + comicInfoGenres)
            .distinctBy { it.lowercase() }
        genre = merged.joinToString(", ")
    }

    listOfNotNull(
        comicInfo.penciller?.value,
        comicInfo.inker?.value,
        comicInfo.colorist?.value,
        comicInfo.letterer?.value,
        comicInfo.coverArtist?.value,
    )
        .flatMap { it.split(", ") }
        .distinct()
        .joinToString(", ") { it.trim() }
        .takeIf { it.isNotEmpty() }
        ?.let { artist = it }

    status = ComicInfoPublishingStatus.toSMangaValue(comicInfo.publishingStatus?.value)
}

// https://anansi-project.github.io/docs/comicinfo/schemas/v2.0
@Suppress("UNUSED")
@Serializable
@XmlSerialName("ComicInfo", "", "")
data class ComicInfo(
    val title: Title?,
    val series: Series?,
    val number: Number?,
    val count: Count?,
    val volume: Volume?,
    val summary: Summary?,
    val year: Year?,
    val writer: Writer?,
    val penciller: Penciller?,
    val inker: Inker?,
    val colorist: Colorist?,
    val letterer: Letterer?,
    val coverArtist: CoverArtist?,
    val translator: Translator?,
    val genre: Genre?,
    val tags: Tags?,
    val web: Web?,
    val languageISO: LanguageISO?,
    val manga: Manga?,
    val publishingStatus: PublishingStatusTachiyomi?,
    val categories: CategoriesTachiyomi?,
    val source: SourceEphyra?,
) {
    @XmlElement(false)
    @XmlSerialName("xmlns:xsd", "", "")
    val xmlSchema: String = "http://www.w3.org/2001/XMLSchema"

    @XmlElement(false)
    @XmlSerialName("xmlns:xsi", "", "")
    val xmlSchemaInstance: String = "http://www.w3.org/2001/XMLSchema-instance"

    @Serializable
    @XmlSerialName("Title", "", "")
    data class Title(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Series", "", "")
    data class Series(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Number", "", "")
    data class Number(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Count", "", "")
    data class Count(@XmlValue(true) val value: Int = -1)

    @Serializable
    @XmlSerialName("Volume", "", "")
    data class Volume(@XmlValue(true) val value: Int = -1)

    @Serializable
    @XmlSerialName("Summary", "", "")
    data class Summary(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Year", "", "")
    data class Year(@XmlValue(true) val value: Int = -1)

    @Serializable
    @XmlSerialName("Writer", "", "")
    data class Writer(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Penciller", "", "")
    data class Penciller(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Inker", "", "")
    data class Inker(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Colorist", "", "")
    data class Colorist(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Letterer", "", "")
    data class Letterer(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("CoverArtist", "", "")
    data class CoverArtist(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Translator", "", "")
    data class Translator(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Genre", "", "")
    data class Genre(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Tags", "", "")
    data class Tags(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Web", "", "")
    data class Web(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("LanguageISO", "", "")
    data class LanguageISO(@XmlValue(true) val value: String = "")

    /**
     * ComicInfo v2.0 Manga field: "Yes", "No", "YesAndRightToLeft".
     * Jellyfin uses this to identify manga content and set reading direction.
     */
    @Serializable
    @XmlSerialName("Manga", "", "")
    data class Manga(@XmlValue(true) val value: String = "")

    // The spec doesn't have a good field for this
    @Serializable
    @XmlSerialName("PublishingStatusTachiyomi", "http://www.w3.org/2001/XMLSchema", "ty")
    data class PublishingStatusTachiyomi(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Categories", "http://www.w3.org/2001/XMLSchema", "ty")
    data class CategoriesTachiyomi(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("SourceEphyra", "http://www.w3.org/2001/XMLSchema", "mh")
    data class SourceEphyra(@XmlValue(true) val value: String = "")
}

enum class ComicInfoPublishingStatus(
    val comicInfoValue: String,
    val sMangaModelValue: Int,
) {
    ONGOING("Ongoing", SManga.ONGOING),
    COMPLETED("Completed", SManga.COMPLETED),
    LICENSED("Licensed", SManga.LICENSED),
    PUBLISHING_FINISHED("Publishing finished", SManga.PUBLISHING_FINISHED),
    CANCELLED("Cancelled", SManga.CANCELLED),
    ON_HIATUS("On hiatus", SManga.ON_HIATUS),
    UNKNOWN("Unknown", SManga.UNKNOWN),
    ;

    companion object {
        fun toComicInfoValue(value: Long): String {
            return entries.firstOrNull { it.sMangaModelValue == value.toInt() }?.comicInfoValue
                ?: UNKNOWN.comicInfoValue
        }

        fun toSMangaValue(value: String?): Int {
            return entries.firstOrNull { it.comicInfoValue == value }?.sMangaModelValue
                ?: UNKNOWN.sMangaModelValue
        }
    }
}

