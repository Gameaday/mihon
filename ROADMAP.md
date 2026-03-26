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

## Architectural Principles

The re-architecture of Ephyra represents a shift from a decade of technical debt—characterized by tightly coupled "God Objects" and synchronous data access—to a modern, enterprise-grade mobile system. This evolution is guided by a **"Heal to Enable Selection"** philosophy, ensuring that core components can be selectively replaced in the future without destabilizing the entire platform.

### 1. The Interactor (Use-Case) Mandate
The most significant shift in business logic is the migration to **Domain Interactors**.
- **The Decision**: Moving logic out of the `ScreenModel` and `Repository` into single-purpose classes (e.g., `GetManga`, `SetReadStatus`).
- **The Rationale**: This detangles the Presentation layer from the Data layer. The UI no longer knows how data is stored; it only knows what action it wants to perform. This isolation ensures that if the database engine changes (e.g., from SQLDelight to Room), the UI remains untouched.

### 2. The Migration to Room: A New Engine
A cornerstone of the future roadmap is the complete replacement of SQLDelight with **Room**.
- **The Decision**: Transitioning from SQL-first (SQLDelight) to an Entity-DAO paradigm (Room).
- **The Rationale**: While SQLDelight provided the foundation, Room is the industry standard for professional Android development. The transition provides:
    - **Superior Observability**: Native integration with `Flow` and `Paging 3` reducing boilerplate to keep the UI in sync.
    - **Modern Tooling**: Deep integration with the Android Studio Database Inspector for professional-level debugging and performance profiling.
    - **Automated Migrations**: Room simplifies handling a decade-old, complex schema via automated migration paths and compile-time SQL verification.
    - **Reduced Manual Friction**: Shifts the burden of writing manual SQL for basic operations back to the compiler.

### 3. Dependency Inversion over Service Location
The codebase is transitioning from **Injekt** (a Service Locator) to **Koin** (true Dependency Injection).
- **Refactoring by Intent**: Architecture now enforces **Constructor Injection** instead of global getters.
- **The Power of Scope**: By utilizing Koin Scopes, dependencies are tied to the lifecycle of a specific feature or screen (like a `MangaId` scope). This eliminates the boilerplate of passing primitive IDs through long chains and ensures memory is reclaimed immediately when the user navigates away.

### 4. Unidirectional Data Flow (UDF)
To eliminate "Main Thread Jank" and race conditions, the UI paradigm follows strict **Unidirectional Data Flow**.
- **The Decision**: Every `ScreenModel` is mandated to emit a single, immutable `ViewState` object and receive a single stream of `Events` or `Intents`.
- **The Rationale**: Standardizing state management ensures UI recompositions are predictable and performant. It prevents bugs where multiple independent data streams fall out of sync.

### 5. Asynchronous Data Persistence
The transition from `AndroidPreferenceStore` (legacy SharedPreferences) to `DataStorePreferenceStore` is a critical "Healing" operation.
- **The Rationale**: Legacy storage was synchronous and frequently blocked the Main UI thread. The new architecture leverages **AndroidX DataStore**, which is fundamentally asynchronous and `Flow`-based.
- **The Principle**: No disk I/O should ever occur on the Main thread. All persistence logic is shifted to `Dispatchers.IO`, ensuring the UI remains fluid at 120 FPS.

### 6. Host-Extension API Preservation
As a host environment for dynamic, APK-based plugins, the re-architecture respects the **Public API Surface**.
- **The Decision**: While internal modules move to the `ephyra.*` namespace, the `source-api` module strictly preserves the `eu.kanade.tachiyomi.source` namespace.
- **The Rationale**: This creates a "Bridge" allowing the app to be modernized internally while maintaining 100% compatibility with thousands of external extensions.

### 7. R8/Proguard as Security & API Boundary
In this re-architecture, the Proguard file is treated as a **Contract** rather than just a shrinking tool.
- **The Principle**: Surgical retention rules protect shared libraries (like `okhttp3` and `jsoup`) that extensions depend on. This prevents "Transitive DLL Bloat" and ensures that minification does not accidentally strip the "Public API" used by dynamic plugins.

---

**Summary of Future Direction**: By rejecting "in-place" substitutions and instead rewriting paradigms, the project is moving toward a state of **Structural Weightlessness**. The eventual goal is a modularized repository where features are isolated, making the codebase intuitive, navigable, and resistant to technical debt.

## Architecture Summary

| Aspect | Choice |
|--------|--------|
| Identity | Canonical ID from tracker (`al:21`, `mal:30013`, `mu:12345`) |
| Dependency Injection | Koin (Constructor Injection) |
| Database Engine | Room (Flow-based Observability) |
| State Management | Unidirectional Data Flow (ViewState/Event) |
| Persistence | AndroidX DataStore (Asynchronous) |
| Business Logic | Domain Interactors (Use-Cases) |
| Extension API | Legacy Compatibility Bridge (`eu.kanade.*`) |
| Search | 4-tier: canonical ID (free) → title (1 call) → alt titles → deep search |
