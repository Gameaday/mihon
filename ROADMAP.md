# Ephyra Roadmap — Authority-First Manga Management

Ephyra adds an **authority-first identity system** on top of a source-based reading model. Every manga can have a canonical identity from MAL, AniList, or MangaUpdates that persists across sources, enabling use cases beyond just reading online.

## User Stories

### 1. The Cataloger (information-only)

*"I read manga in print, on other apps, or at the library. I want to track what I've read and remember where I left off."*

**What works today:**
- Add any series via tracker search (MAL, AniList, MangaUpdates, Kitsu, Bangumi, Shikimori)
- Track reading progress, scores, start/finish dates, and reading status per tracker
- **Authority chapters** generated from tracker metadata — see chapter list, mark chapters as read/bookmarked even without a content source. If the tracker knows there are 108 chapters, you get Chapter 1–108 to track your progress through
- Write personal **notes** on any manga entry (dedicated notes screen per manga)
- Organize into **categories** with custom names and ordering
- No source needed — the app stores a canonical ID (`al:21`) and won't show health warnings for manga that never had chapters
- Sync progress back to tracker sites to keep your online profile current
- **Backup/restore** preserves all tracking data, notes, canonical IDs, and categories
- If you later add a content source, your read progress carries forward — authority chapters merge cleanly with source chapters

### 2. The Existing Reader (tracker import)

*"I already have a reading list on MAL/AniList. I want to bring my whole library in without starting from scratch."*

**What works today:**
- **Import from MyAnimeList** — one tap in Settings → Tracking imports your entire MAL reading list
- Each manga creates a library entry with canonical ID, cover art, and metadata
- Reading status (reading, completed, on hold, dropped, plan to read) transfers with chapter progress
- **Authority chapters** auto-generated from tracker's total chapter count, with chapters marked as read matching your MAL progress
- Duplicate detection — manga already in your library are skipped
- After import, you can link content sources for any manga you want to read online, or keep tracking-only

**What could be built next:**
- Import from AniList reading list
- Two-way sync (push new library additions back to tracker)
- Import with volume-level chapter data from richer metadata sources

### 3. The Local Reader (existing library)

*"I have manga files (CBZ, EPUB, folders) on my device. I want to organize and read them with progress tracking."*

**What works today:**
- **Local source** imports manga from device storage (CBZ archives, EPUB, folder structures)
- Link local manga to trackers for canonical identity and progress sync
- Organize local manga into categories, add personal notes
- Local sources are **excluded from health detection** — no false DEAD/DEGRADED warnings
- Health banner hidden for local manga in detail view
- Download chapters for offline reading; downloaded content survives source changes

### 4. The Local Content Creator

*"I add my own scans or downloads to the local library over time."*

**What works today:**
- Add files to the local source directory; they appear on next library refresh
- Same organization tools as any other manga: categories, notes, tracker links
- Mix local and online manga in the same library with unified filtering and sorting

### 5. The Online Reader

*"I read from online sources and want a smooth reading experience with progress tracking."*

**What works today:**
- Browse and search across installed extension sources
- Track progress via MAL/AniList/MangaUpdates — canonical ID auto-set on tracker bind
- **Alternative titles** pulled from AniList (romaji, native, synonyms) stored as JSON
- Download chapters for offline reading
- Library filters: downloaded, unread, started, bookmarked, completed status
- Sort by title, chapters, latest update, date added, or unread count
- Display modes: compact grid, comfortable grid, or list — configurable per category

### 6. The Migrator (source changes)

*"My source died or I want to switch. I need to move my manga without losing progress."*

**What works today:**
- **Automatic health detection** during library updates — classifies sources as HEALTHY, DEGRADED, or DEAD
- **Visual warnings everywhere**: ⚠ badge on library covers, colored source name in detail, warning banner with "Migrate" button
- **Notifications**: dead/degraded alerts after updates, migration reminder after 3 days dead
- **Smart migration search**: canonical ID (free, instant) → primary title → alt titles → near-match → deep search, with match confidence percentage
- **Library filter** to show only dead/degraded manga for batch triage
- Health status and dead_since timestamps survive backup/restore

### 7. The Progress Tracker (offline sync)

*"I read offline or across devices. I want my reading position to stay synced."*

**What works today:**
- Tracker sync on chapter read — updates MAL/AniList/MangaUpdates automatically
- Reading progress persisted locally per chapter (last page read)
- History tracks when each chapter was read
- **Backup/restore** captures full state: library, chapters, tracking, history, categories, notes
- Restore on a new device picks up exactly where you left off

### 8. The Organizer

*"I have a large library and need to keep it tidy."*

**What works today:**
- **Categories** with custom names, drag-to-reorder, per-category display and sort settings
- **Filters**: downloaded, unread, started, bookmarked, completed, dead/degraded sources — all combinable as include/exclude
- **Sorting**: alphabetical, by chapter count, latest update, date added, unread count
- **Display modes**: compact grid, comfortable grid, list — set globally or per category
- **Notes** per manga for personal annotations
- Library health banner shows at-a-glance count of manga needing attention

### 9. The Sharer

*"I want to share my favorites, collections, and recommendations with friends."*

**What works today:**
- **Share manga** link directly from manga detail screen (shares source URL)
- **Share cover** image from manga detail
- **Backup export** with granular options: library entries, categories, chapters, tracking, history, app settings — mix and match
- Backup files are portable `.tachibk` format for sharing between devices

**What could be built next:**
- Share manga with the user's **personal notes** included (formatted text or image card)
- **Collections** — curated, ordered groups of series (beyond categories). Automatic collections (e.g., "Completed this month"), smart collections (by genre/author/status), and custom hand-picked collections
- **Collection notes** — add descriptions, commentary, or suggested reading order to collections
- Share collections as formatted lists with notes, custom ordering, and cover art
- Export a collection as a standalone shareable file (subset of library)

### 10. The Explorer

*"I want to find new series similar to what I already enjoy."*

**What works today:**
- **Browse sources** with per-source filters (genre, status, popularity)
- **Global search** across all installed sources simultaneously
- Source-specific popular/latest listings

**What could be built next:**
- "More like this" recommendations based on linked tracker data (genre, author, similar users)
- Tracker-based discovery: "users who read X also read Y"
- "Like this" button on manga detail to find similar series across sources

## Fork Features Summary

| Feature | Status |
|---------|--------|
| Canonical ID from trackers | ✅ Auto-set on bind |
| Alternative titles (AniList) | ✅ JSON storage, used in search |
| Source health detection | ✅ DEAD/DEGRADED/HEALTHY on update |
| Health UI (banner, badge, color) | ✅ All library + detail views |
| Health notifications | ✅ Post-update + 3-day migration reminder |
| Smart migration search | ✅ 4-tier with confidence % |
| Library health filter | ✅ TriState include/exclude |
| Local/stub source safety | ✅ Excluded from health detection |
| Backup completeness | ✅ Canonical ID, status, dead_since |
| Design tokens | ✅ Consistent spacing system |
| Tracker list import (MAL) | ✅ One-tap import of reading list |
| Authority chapters | ✅ Chapters from tracker metadata for sourceless manga |
| Manga notes | ✅ Upstream feature, fully supported |
| Categories + organization | ✅ Upstream feature, fully supported |
| Tracker sync | ✅ Upstream feature, fully supported |
| Share manga with notes | 📋 Next up |
| Collections (custom, smart, auto) | 📋 Next up |
| Collection notes + sharing | 📋 Next up |
| Recommendations / "like this" | 📋 Planned |
| AniList list import | 📋 Planned |
| Cross-media authority sources | 🔮 Future vision |

145 unit tests. Zero compiler warnings.

## Future Vision: Cross-Media Collections

The authority-first model isn't limited to manga. The canonical ID system, tracker integration, authority chapters, collections, and sharing features could extend to other media types:

| Media Type | Potential Authority Sources | Tracker Integration |
|-----------|---------------------------|-------------------|
| **Light Novels** | MAL (already tracks LNs), AniList, NovelUpdates | Reading progress, volumes, chapters |
| **Books** | MAL (LN overlap), Goodreads, OpenLibrary | Reading status, ratings |
| **Anime** | MAL, AniList, Kitsu | Episode progress, watch status |
| **Games** | IGDB, HowLongToBeat, Steam | Play status, completion |
| **Movies/TV** | TMDB, Trakt, IMDb | Watch status, ratings |

**Cross-media collections** would enable users to curate multi-format series guides — for example, sharing all .hack media (manga, anime, games, light novels) with personal notes on each entry and a recommended experience order. A single shared collection could include:
- Manga volumes with reading progress
- Anime seasons with episode tracking
- Light novels with chapter progress
- Games with completion status
- User notes explaining the recommended order and why

This builds on the existing architecture: canonical IDs identify entries across sources, authority chapters provide progress tracking without content, and the collection/sharing system provides the presentation layer.

**Not currently planned** — documenting the vision to guide architectural decisions that keep these doors open.

## Architecture

| Aspect | Choice |
|--------|--------|
| Identity | Canonical ID from tracker (`al:21`, `mal:30013`, `mu:12345`) |
| Alt titles | JSON array in DB, backward-compatible with legacy pipe-separated |
| Health detection | Chapter count comparison (70% threshold), zero extra API calls |
| Search | 4-tier: canonical ID (free) → title (1 call) → alt titles → deep search |
| Authority chapters | Generated from tracker `total_chapters`, `authority://` URL scheme |
| Tracker import | MAL reading list → manga entries + tracker binding + authority chapters |
