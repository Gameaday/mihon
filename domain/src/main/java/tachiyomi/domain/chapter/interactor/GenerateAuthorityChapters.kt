package tachiyomi.domain.chapter.interactor

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository

/**
 * Generates placeholder chapters for authority-only manga (no content source).
 * Chapters are numbered 1..totalChapters and named "Chapter N".
 * Chapters up through lastChapterRead are marked as read.
 *
 * This enables progress tracking for users who read elsewhere (print, other apps)
 * and later eases migration if they add a content source.
 */
class GenerateAuthorityChapters(
    private val chapterRepository: ChapterRepository,
) {

    /**
     * Generates authority chapters for a manga, skipping any that already exist.
     *
     * @param mangaId The manga to generate chapters for
     * @param totalChapters Total number of chapters to generate (from tracker metadata)
     * @param lastChapterRead Chapters 1..lastChapterRead will be marked as read
     * @return Number of chapters actually created (excludes already-existing ones)
     */
    suspend fun await(
        mangaId: Long,
        totalChapters: Int,
        lastChapterRead: Int = 0,
    ): Int {
        if (totalChapters <= 0) return 0

        // Check existing chapters to avoid duplicates
        val existing = chapterRepository.getChapterByMangaId(mangaId)
        val existingNumbers = existing.map { it.chapterNumber }.toSet()

        val now = System.currentTimeMillis()
        val newChapters = (1..totalChapters)
            .filter { num -> num.toDouble() !in existingNumbers }
            .map { num ->
                Chapter.create().copy(
                    mangaId = mangaId,
                    url = "authority://chapter/$num",
                    name = "Chapter $num",
                    chapterNumber = num.toDouble(),
                    sourceOrder = (totalChapters - num).toLong(), // Reverse order so Ch 1 is last
                    read = num <= lastChapterRead,
                    dateFetch = now,
                    dateUpload = 0L,
                )
            }

        if (newChapters.isEmpty()) return 0

        chapterRepository.addAll(newChapters)

        // Also mark any pre-existing chapters as read if within read range
        if (lastChapterRead > 0) {
            val toMarkRead = existing
                .filter { !it.read && it.chapterNumber <= lastChapterRead }
                .map { ChapterUpdate(id = it.id, read = true) }
            if (toMarkRead.isNotEmpty()) {
                chapterRepository.updateAll(toMarkRead)
            }
        }

        return newChapters.size
    }
}
