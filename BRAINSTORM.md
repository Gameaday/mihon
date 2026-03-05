# Mihon Fork — Feature & Architecture Brainstorm

> Living document of ideas, architectural visions, and refinement opportunities.
> Categorized by area with implementation complexity and priority indicators.

---

## 🌟 Central Feature: Identity-Based Source Resolution — Complete Implementation Plan

### Vision

Users add manga **by identity**, not by source. The app automatically discovers which
installed extension sources carry that series, records the mappings locally, and fetches
chapters from the best available source using a configurable strategy — **without hitting
every API on every refresh**.

### Design Principles

1. **One-time discovery, permanent local cache** — Search sources once, store results in a
   local `source_mappings` table. Never search again unless the user asks.
2. **Only call sources we know have the series** — Library refresh reads from the local
   mapping table, not from a blind search across all installed extensions.
3. **Tracker IDs as canonical identity** — AniList/MAL/MangaUpdates `remote_id` is the
   ground truth. Fuzzy matching is a fallback, not the default path.
4. **Graceful degradation** — If no tracker is linked, the system falls back to the
   current single-source behavior. No forced migration.
5. **Respect remote servers** — Rate-limiting, staggered requests, exponential backoff,
   and configurable concurrency limits.

---

### Part 1: Database Schema

#### New Table: `source_mappings`

Records which extension sources carry a given manga. Populated once during discovery,
never touched again during normal library refresh.

```sql
-- Migration 12.sqm
CREATE TABLE source_mappings (
    _id            INTEGER NOT NULL PRIMARY KEY,
    manga_id       INTEGER NOT NULL,          -- FK → mangas._id
    source_id      INTEGER NOT NULL,          -- Extension source ID (MD5-based)
    source_url     TEXT    NOT NULL,           -- URL path on that source (e.g. "/manga/12345")
    priority       INTEGER NOT NULL DEFAULT 0, -- User-set priority (lower = preferred)
    confidence     REAL    NOT NULL DEFAULT 1.0, -- Match confidence 0.0–1.0
    confirmed      INTEGER NOT NULL DEFAULT 0,  -- 1 = user manually confirmed
    last_checked   INTEGER NOT NULL DEFAULT 0,  -- Epoch seconds of last chapter fetch
    chapter_count  INTEGER NOT NULL DEFAULT 0,  -- Cached chapter count from this source
    status         INTEGER NOT NULL DEFAULT 0,  -- 0=ACTIVE, 1=STALE (temp failure), 2=NOT_FOUND (confirmed absent)
    created_at     INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    UNIQUE (manga_id, source_id) ON CONFLICT REPLACE,
    FOREIGN KEY(manga_id) REFERENCES mangas(_id) ON DELETE CASCADE
);

CREATE INDEX idx_source_mappings_manga ON source_mappings(manga_id);
CREATE INDEX idx_source_mappings_source ON source_mappings(source_id);
CREATE INDEX idx_source_mappings_status ON source_mappings(status) WHERE status = 0;
```

#### Modify `mangas` Table

```sql
-- Also in migration 12.sqm
ALTER TABLE mangas ADD COLUMN canonical_tracker_id INTEGER;
    -- Stores the remote_id VALUE from manga_sync (e.g. AniList media ID 21 for One Piece)
    -- NOT a FK to manga_sync._id — this is the tracker service's own ID for the series
ALTER TABLE mangas ADD COLUMN canonical_tracker_type INTEGER; -- 1=MAL, 2=AniList, 7=MangaUpdates
ALTER TABLE mangas ADD COLUMN source_strategy INTEGER NOT NULL DEFAULT 0;
    -- 0=single (legacy), 1=hierarchy, 2=round-robin, 3=quality
```

**Why `canonical_tracker_id`?** The existing `manga_sync` table already stores `remote_id`
for each tracker service. But a manga can have multiple tracker entries. We pick ONE as the
canonical identity anchor — preferably AniList (most extensions embed it) or MAL.

**Why on the manga row?** To avoid a JOIN on every library query. The canonical ID is read
thousands of times but written once.

#### Modify `chapters` Table

```sql
-- Also in migration 12.sqm
ALTER TABLE chapters ADD COLUMN resolved_source_id INTEGER;
    -- Which source this chapter was actually fetched from
    -- NULL = fetched from manga.source (legacy behavior)
```

This lets chapters come from different sources within the same manga. When reading, we
know exactly which source to call for page URLs.

---

### Part 2: Source Discovery Protocol

This is the most API-sensitive part. Discovery happens **once per manga**, not on every
refresh.

#### When Does Discovery Run?

| Trigger | What Happens |
|---------|-------------|
| User adds manga to library | If tracker linked → discover. If not → single-source (legacy). |
| User links tracker to existing manga | Backfill discovery for that manga. |
| User taps "Find more sources" on manga detail | Manual discovery for one manga. |
| Bulk discovery (settings) | Background job processes library, rate-limited. |

Discovery **never** runs during normal library refresh.

#### Discovery Algorithm

```
DiscoverSourcesForManga(manga, trackerRemoteId):
    
    1. CANDIDATE SOURCES
       candidates = installedCatalogueSources
           .filter { it.lang in enabledLanguages }
           .filter { it.id not in disabledSources }
           .filter { no existing mapping with status=active for (manga.id, it.id) }

    2. SEARCH PHASE (rate-limited, sequential per source, parallel across sources capped at 3)
       for source in candidates (with Semaphore(3)):
           try:
               // Use the EXISTING search API - same call the browse screen makes
               results = source.getSearchManga(page=1, query=cleanTitle(manga.title), filters=emptyList())
               
               bestMatch = scoreMatches(results.mangas, manga, trackerRemoteId)
               if bestMatch != null && bestMatch.score >= 0.6:
                   INSERT INTO source_mappings (
                       manga_id = manga.id,
                       source_id = source.id,
                       source_url = bestMatch.url,
                       confidence = bestMatch.score,
                       confirmed = 0,  // Always 0 initially; set to 1 only by explicit user action
                   )
           catch RateLimitException:
               delay(exponentialBackoff)
               retry once
           catch Exception:
               skip this source (log for debugging)

    3. OPTIONAL: Fetch chapter counts from discovered sources
       // Deferred — only done when user views manga detail or on next library refresh
       // This avoids extra API calls during discovery
```

#### Match Scoring Algorithm

```
scoreMatches(candidates: List<SManga>, localManga: Manga, trackerRemoteId: Long?):
    
    for candidate in candidates:
        score = 0.0
        
        // Level 1: Tracker ID exact match (if source embeds tracker IDs)
        if candidate has tracker metadata matching trackerRemoteId:
            score = 1.0  // Perfect match
            return (candidate, score)
        
        // Level 2: Title similarity
        titleScore = normalizedLevenshtein(
            cleanTitle(localManga.title),
            cleanTitle(candidate.title)
        )
        score += titleScore * 0.5  // 50% weight
        
        // Level 3: Author match
        if localManga.author != null && candidate.author != null:
            authorScore = normalizedLevenshtein(localManga.author, candidate.author)
            score += authorScore * 0.25  // 25% weight
        else:
            score += 0.125  // Neutral if unknown
        
        // Level 4: Description overlap (cheap keyword matching)
        if both have descriptions:
            descScore = keywordOverlapScore(localManga.description, candidate.description)
            score += descScore * 0.15  // 15% weight
        
        // Level 5: Status match bonus
        if localManga.status == candidate.status:
            score += 0.10  // 10% bonus
    
    return best candidate with score >= 0.6, or null
```

**Why 0.6 threshold?** The existing `BaseSmartSearchEngine` uses 0.4 for
NormalizedLevenshtein which maps to ~0.6 in our weighted composite. This is proven
reliable by the migration system.

**Title Cleaning** — Reuse the existing `cleanTitle()` from `BaseSmartSearchEngine`:
strips brackets, special chars, normalizes whitespace, handles Cyrillic.

---

### Part 3: Chapter Resolution Strategies

During library refresh, when fetching chapters for a manga with `source_strategy != 0`:

#### Strategy 0: Single Source (Legacy)
- Behavior: Identical to current Mihon. `manga.source` is the only source.
- API calls: 1 per manga per refresh.
- **Default for all existing manga. Zero breaking changes.**

#### Strategy 1: Source Hierarchy (Recommended Default for Multi-Source)
```
resolveChapters(manga):
    mappings = source_mappings
        .where(manga_id = manga.id, status = active)
        .orderBy(priority ASC)  // User-defined order, or auto-ordered by confidence
    
    for mapping in mappings:
        source = sourceManager.get(mapping.source_id)
        if source == null: continue  // Extension uninstalled
        
        try:
            chapters = source.getChapterList(SManga(url=mapping.source_url))
            mapping.last_checked = now()
            mapping.chapter_count = chapters.size
            return (chapters, mapping.source_id)
        catch:
            mapping.status = stale  // Mark for retry later
            continue  // Fall through to next source
    
    // All sources failed → fall back to manga.source (legacy)
    return legacyFetch(manga)
```

**API calls**: 1 per manga per refresh (same as current!). Only falls through on failure.

#### Strategy 2: Round Robin
```
resolveChapters(manga):
    mappings = source_mappings
        .where(manga_id = manga.id, status = active)
        .orderBy(last_checked ASC)  // Least recently used first
    
    mapping = mappings.first()
    // Same fetch logic as hierarchy, but rotation spreads load
```

**API calls**: 1 per manga per refresh (same as current!). Load distributed across sources.

#### Strategy 3: Quality Selection (Premium — Higher API Cost)
```
resolveChapters(manga):
    mappings = source_mappings.where(manga_id = manga.id, status = active)
    
    // First, get chapter lists from all sources (parallel, Semaphore(3))
    allChapterLists = mappings.parallelMap { mapping ->
        source.getChapterList(SManga(url=mapping.source_url))
    }
    
    // For the latest unread chapter, fetch first page from each source
    latestChapter = findLatestUnread(allChapterLists)
    pageQualities = mappings.parallelMap { mapping ->
        pages = source.getPageList(latestChapter)
        firstPageUrl = pages.first().imageUrl
        estimateQuality(firstPageUrl)  // HEAD request for Content-Length, or download tiny range
    }
    
    bestSource = pageQualities.maxBy { it.quality }
    return (allChapterLists[bestSource.index], bestSource.sourceId)
```

**API calls**: N per manga per refresh (where N = number of mapped sources).
**Mitigation**: Cache quality scores. Only re-check quality every 30 days or when user requests.

---

### Part 4: API Cost Analysis

#### Current System (Baseline)
| Operation | API Calls | Frequency |
|-----------|-----------|-----------|
| Library refresh (100 manga) | 100 | Every 12h (default) |
| Chapter fetch per manga | 1 | Per refresh |
| **Total per day** | **~200** | |

#### New System — Strategy 1 (Hierarchy)
| Operation | API Calls | Frequency |
|-----------|-----------|-----------|
| **One-time discovery** (100 manga × 10 sources) | **~1000** | **Once ever** |
| Library refresh (100 manga) | 100 | Every 12h |
| Chapter fetch per manga | 1 | Per refresh |
| **Total per day (steady state)** | **~200** | **Same as current!** |

#### New System — Strategy 2 (Round Robin)
| Operation | API Calls | Frequency |
|-----------|-----------|-----------|
| One-time discovery | ~1000 | Once ever |
| Library refresh (100 manga) | 100 | Every 12h |
| **Per-source load** | **~10-20** | **Per source per refresh** |
| **Total per day** | **~200** | **Same, but distributed** |

**Key insight**: The only additional API cost is the one-time discovery phase.
Ongoing refresh is identical to current behavior (1 call per manga per refresh).

#### Discovery Rate Limiting

```
DiscoveryRateLimiter:
    maxConcurrentSources = 3          // Never hit more than 3 sources simultaneously
    delayBetweenSearches = 2000ms     // 2 seconds between searches on same source
    maxSearchesPerSource = 50/hour    // Hard cap per extension
    retryBackoff = [5s, 15s, 60s]     // Exponential on rate limit
    
    // For bulk discovery of 100 manga × 10 sources:
    // 100 searches × 2s delay = ~200s per source = ~3.3 minutes per source
    // 10 sources at 3 concurrent = ~4 rounds = ~13 minutes total
    // Very reasonable for a one-time background operation
```

---

### Part 5: Matching Reliability — Why It Will Work

#### Tier 1: Tracker ID Matching (Covers ~70% of Cases)

The `manga_sync` table already stores `remote_id` for services like AniList (id=2),
MAL (id=1), and MangaUpdates (id=7). Many extension sources embed these IDs in their
metadata or URL patterns.

**Example**: A user has "One Piece" tracked on AniList with `remote_id = 21`. When
we search extension source X for "One Piece" and it returns a result with AniList ID
21 in its metadata, that's a **guaranteed match**.

Even without embedded IDs: the tracker `remote_id` is unique per series. If two manga
in our library have the same AniList `remote_id`, they're the same series. We already
use this in `getDuplicateLibraryManga`:

```sql
-- Already in mangas.sq!
track_dupes AS (
    SELECT S2.manga_id
    FROM manga_sync S1
    INNER JOIN manga_sync S2
    ON S1.sync_id = S2.sync_id
    AND S1.remote_id = S2.remote_id
    AND S1.manga_id != S2.manga_id
    WHERE S1.manga_id = :id
)
```

#### Tier 2: Smart Fuzzy Search (Covers ~25% of Remaining)

The existing `BaseSmartSearchEngine` in the migration system is **battle-tested**.
It uses NormalizedLevenshtein distance with 5 query variations:
- Full cleaned title
- Two largest words
- Largest word
- First two words
- First word

This already works well for source migration. We reuse it exactly.

#### Tier 3: Manual Confirmation (Covers the Last ~5%)

For low-confidence matches (0.6–0.85), the UI shows a confirmation dialog:
"We think [Source X] has this series as '[Title Y]'. Is this correct?"

User confirms → `confirmed = 1` in source_mappings. Never asked again.

#### Negative Caching — Avoiding Fruitless Searches

When a source search returns zero results for a manga, store a **negative mapping**:

```sql
INSERT INTO source_mappings (manga_id, source_id, source_url, status)
VALUES (:mangaId, :sourceId, '', 2);  -- status=2 (NOT_FOUND): search returned no results
```

On subsequent discovery runs (e.g., user installs new extension), we skip sources
that already have status=NOT_FOUND for that manga. This prevents re-searching sources that
don't have the series.

**Expiry**: Negative cache entries expire after 90 days (sources add new series).

---

### Part 6: Integration with Existing Systems

#### Library Update Flow (Modified)

Current flow:
```
LibraryUpdateJob.updateManga(manga):
    source = sourceManager.get(manga.source)
    chapters = source.getChapterList(manga.toSManga())
    syncChaptersWithSource(chapters)
```

New flow:
```
LibraryUpdateJob.updateManga(manga):
    if manga.source_strategy == 0 (single):
        // LEGACY PATH — zero changes, zero risk
        source = sourceManager.get(manga.source)
        chapters = source.getChapterList(manga.toSManga())
        syncChaptersWithSource(chapters, sourceId = manga.source)
    else:
        // MULTI-SOURCE PATH
        (chapters, sourceId) = chapterResolver.resolve(manga)
        syncChaptersWithSource(chapters, sourceId)
```

**Key**: The `Semaphore(5)` concurrency limit in `updateChapterList()` still applies.
Multi-source manga still count as 1 unit of work (we call 1 source, not N).

#### Chapter Reading (Modified)

Current flow:
```
ReaderViewModel.loadChapter(chapter):
    source = sourceManager.get(manga.source)
    pages = source.getPageList(chapter.toSChapter())
```

New flow:
```
ReaderViewModel.loadChapter(chapter):
    sourceId = chapter.resolved_source_id ?: manga.source
    source = sourceManager.get(sourceId)
    pages = source.getPageList(chapter.toSChapter())
```

**Key**: The `resolved_source_id` on each chapter tells us exactly which source to
call for pages. No searching, no heuristics, no extra API calls.

#### Migration System — Becomes Optional

With multi-source mappings, "migration" becomes "re-resolve sources" or "change
preferred source" — a UI operation, not a data migration. Users can:
- Reorder source priorities (drag-and-drop)
- Remove a dead source mapping
- Manually add a source mapping
- Trigger re-discovery

---

### Part 7: Domain Model Changes

#### New Domain Models

```kotlin
// domain/src/main/java/tachiyomi/domain/manga/model/SourceMapping.kt
data class SourceMapping(
    val id: Long,
    val mangaId: Long,
    val sourceId: Long,
    val sourceUrl: String,
    val priority: Int,
    val confidence: Float,
    val confirmed: Boolean,
    val lastChecked: Long,
    val chapterCount: Int,
    val status: MappingStatus,
    val createdAt: Long,
)

enum class MappingStatus { ACTIVE, STALE, NOT_FOUND }

// Extension for resolution strategy
enum class SourceStrategy {
    SINGLE,      // Legacy behavior
    HIERARCHY,   // Ordered preference list
    ROUND_ROBIN, // Rotate to spread load
    QUALITY,     // Check quality and pick best
}
```

#### New Use Cases

```kotlin
// domain/src/main/java/tachiyomi/domain/manga/interactor/

// One-time discovery
class DiscoverSourcesForManga(
    private val sourceManager: SourceManager,
    private val mappingRepository: SourceMappingRepository,
    private val smartSearch: BaseSmartSearchEngine,
)

// Chapter resolution during library refresh
class ResolveChapterSource(
    private val sourceManager: SourceManager,
    private val mappingRepository: SourceMappingRepository,
)

// Get mappings for manga detail screen
class GetSourceMappings(
    private val mappingRepository: SourceMappingRepository,
)

// Update mapping priority (drag-and-drop reorder)
class UpdateMappingPriority(
    private val mappingRepository: SourceMappingRepository,
)
```

#### Modified Domain Models

```kotlin
// Manga.kt — add 2 fields
data class Manga(
    // ... existing fields ...
    val canonicalTrackerId: Long? = null,      // AniList/MAL remote_id
    val canonicalTrackerType: Int? = null,      // Which tracker (1=MAL, 2=AniList, etc.)
    val sourceStrategy: SourceStrategy = SourceStrategy.SINGLE,
)

// Chapter.kt — add 1 field
data class Chapter(
    // ... existing fields ...
    val resolvedSourceId: Long? = null,  // Which source this chapter came from
)
```

---

### Part 8: Implementation Phases

#### Phase 1: Schema & Domain Foundation (Week 1-2)

**Files to create/modify:**

| File | Change |
|------|--------|
| `data/src/main/sqldelight/tachiyomi/migrations/12.sqm` | New migration with schema changes |
| `data/src/main/sqldelight/tachiyomi/data/source_mappings.sq` | New SQLDelight file |
| `domain/.../manga/model/SourceMapping.kt` | New domain model |
| `domain/.../manga/model/Manga.kt` | Add 3 new fields |
| `domain/.../chapter/model/Chapter.kt` | Add `resolvedSourceId` |
| `mangas.sq` | Add columns, update queries |
| `chapters.sq` | Add column, update queries |

**Deliverable**: Schema exists, builds, migrates cleanly. No behavioral changes.

#### Phase 2: Discovery Engine (Week 3-4)

**Files to create/modify:**

| File | Change |
|------|--------|
| `domain/.../manga/interactor/DiscoverSourcesForManga.kt` | New use case |
| `domain/.../manga/repository/SourceMappingRepository.kt` | New repository interface |
| `data/.../manga/SourceMappingRepositoryImpl.kt` | Implementation |
| `app/.../data/library/SourceDiscoveryJob.kt` | Background discovery worker |

**Deliverable**: Can discover sources for a single manga. Manual trigger only.

#### Phase 3: Chapter Resolution (Week 5-6)

**Files to create/modify:**

| File | Change |
|------|--------|
| `domain/.../manga/interactor/ResolveChapterSource.kt` | New use case |
| `app/.../data/library/LibraryUpdateJob.kt` | Integrate resolver |
| `app/.../ui/reader/ReaderViewModel.kt` | Use `resolvedSourceId` |
| `data/.../chapter/ChapterRepositoryImpl.kt` | Handle new column |

**Deliverable**: Library refresh uses source mappings. Reading uses resolved source.

#### Phase 4: UI Integration (Week 7-8)

**Files to create/modify:**

| File | Change |
|------|--------|
| `app/.../presentation/manga/MangaScreen.kt` | Show source mappings |
| `app/.../presentation/manga/components/SourceMappingList.kt` | New component |
| `app/.../presentation/more/settings/screen/SettingsLibraryScreen.kt` | Strategy setting |
| `app/.../ui/manga/MangaScreenModel.kt` | Source management actions |

**Deliverable**: Users can see/manage source mappings per manga, set strategy.

#### Phase 5: Auto-Discovery on Library Add (Week 9-10)

**Files to create/modify:**

| File | Change |
|------|--------|
| `app/.../ui/manga/MangaScreenModel.kt` | Trigger discovery on favorite |
| `app/.../data/library/LibraryUpdateJob.kt` | Batch discovery option |

**Deliverable**: Adding manga to library auto-discovers sources. Full feature complete.

---

### Part 9: Backwards Compatibility

#### Zero Breaking Changes for Existing Users

1. **Default strategy is SINGLE (0)** — All existing manga continue to work exactly as before.
2. **New columns have defaults** — `canonical_tracker_id = NULL`, `source_strategy = 0`, `resolved_source_id = NULL`.
3. **Multi-source is opt-in** — Users enable per-manga or set a global default.
4. **Extensions unchanged** — No source-api changes needed. Extensions are not modified.
5. **Tracker integration unchanged** — We read `remote_id` from `manga_sync` but don't modify it.

#### Gradual Adoption Path

```
Day 1: Update installs. Everything works identically.
Day 2: User goes to manga detail → sees "Find more sources" button.
Day 3: User enables hierarchy strategy for one manga.
Day 7: User sets hierarchy as default for new manga.
Day 30: User has 50 manga with multi-source. Library refresh is same speed, but fallback works.
```

---

### Part 10: Technical Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Discovery overloads extension APIs | `Semaphore(3)` + 2s delay between searches + per-source hourly caps |
| False positive matches | 0.6 minimum threshold + user confirmation UI for < 0.9 |
| Source removes series | `status = STALE` on fetch failure, auto-falls to next source |
| Extension uninstalled | `sourceManager.get()` returns null → skip mapping, use next |
| Schema migration fails | SQLDelight migration is atomic; `ALTER TABLE ADD COLUMN` is safe |
| Performance regression | All multi-source logic is behind `source_strategy != 0` check |
| Too many mappings | Capped at 10 sources per manga. UI shows "top 5" by default |

---

### Part 11: Summary — Why This Design Works

| Concern | How It's Addressed |
|---------|-------------------|
| **API cost** | Discovery is one-time. Ongoing refresh = 1 call/manga (same as current). |
| **Reliability** | Tracker IDs provide guaranteed matches for ~70% of cases. |
| **Performance** | Zero overhead for legacy (strategy=0) manga. Multi-source adds 1 DB read. |
| **Server respect** | Rate limiting, staggered requests, negative caching, configurable concurrency. |
| **User control** | Per-manga strategy override, priority reordering, manual confirmation. |
| **Backwards compat** | 100% compatible. Opt-in only. Default = unchanged behavior. |
| **Complexity** | 5 phases, each independently testable and shippable. |
| **Extensibility** | New strategies can be added as enum values without schema changes. |

---

### Part 12: Honest Assessment — Pros, Cons & Open Challenges

#### ✅ Pros

1. **Transformative UX** — Eliminates the biggest friction in manga readers: manually
   finding and switching sources. Users think in terms of "I read One Piece," not "I read
   One Piece on MangaDex via extension X."

2. **Zero ongoing API cost increase** — Strategies 1 (Hierarchy) and 2 (Round Robin) make
   exactly the same number of API calls per refresh as the current single-source system.
   The only additional cost is one-time discovery.

3. **Automatic failover** — If a source goes down, the next refresh silently falls through
   to the next source in the hierarchy. No user intervention needed, no lost reading
   progress. Today, a dead source means a broken manga until the user manually migrates.

4. **Server load distribution** — Round Robin naturally spreads traffic across sources.
   No single extension server bears all the load from this app's users.

5. **100% backwards compatible** — Every existing manga stays on `strategy=SINGLE` by
   default. The feature is purely opt-in. Users who never touch it see zero changes.

6. **Builds on proven infrastructure** — The matching algorithm reuses
   `BaseSmartSearchEngine` (battle-tested in migration), tracker IDs from `manga_sync`
   (already stored), and the existing `Source.getSearchManga()` API (no extension changes).

7. **Incremental delivery** — Each of the 5 phases is independently shippable and testable.
   Phase 1 (schema) can ship without any behavioral changes.

8. **Eliminates manual migration** — Today, when a source dies, users must manually search
   for the series on another source and migrate. With mappings already recorded, "migration"
   becomes clicking a button to promote source #2 to #1.

#### ❌ Cons

1. **Complexity budget** — This is the single largest feature addition in the app's history.
   5 phases, ~15 new/modified files, 1 new database table, 3 modified tables. Risk of
   introducing bugs in the critical library-refresh path.

2. **Discovery phase has real API cost** — For a user with 100 manga and 10 installed
   sources, initial discovery makes ~1,000 search API calls (spread over ~13 minutes with
   rate limiting). While this is a one-time cost, if many users upgrade simultaneously,
   extension servers could see a temporary spike.

3. **Matching is inherently imperfect** — Even with the hybrid approach (tracker IDs +
   fuzzy matching + metadata scoring), some matches will be wrong. A false positive could
   mean fetching chapters from the wrong series — which is worse than no match at all.
   The 0.6 confidence threshold is a judgment call, not a guarantee.

4. **Quality Selection strategy (Strategy 3) is expensive** — It requires fetching page
   lists from multiple sources per chapter, plus HEAD requests to estimate image quality.
   While cacheable, it's the one strategy that breaks the "same API cost" promise.

5. **Tracker dependency for best results** — The highest-confidence matching relies on
   tracker IDs. Users who don't use AniList/MAL/MangaUpdates get a degraded experience
   (fuzzy matching only). This could create an implicit pressure to use trackers.

6. **Storage growth** — The `source_mappings` table grows with `manga_count × sources_per_manga`.
   For 500 manga × 10 sources = 5,000 rows. Manageable, but not free. Negative cache entries
   add to this (though they expire after 90 days).

7. **Extension ecosystem fragility** — The system assumes extensions expose a working
   `getSearchManga()` API. Some extensions have rate limits, CAPTCHAs, or broken search.
   Discovery silently fails for these, which could leave gaps in source coverage.

#### 🔶 Open Challenges

**Challenge 1: Tracker ID Coverage Gap**

Not all extensions embed AniList/MAL IDs in their search results. For those that don't,
we fall back to fuzzy title matching. But titles vary wildly across sources:
- English: "Attack on Titan"
- Japanese: "Shingeki no Kyojin"  
- Source-specific: "Attack on Titan (Shingeki no Kyojin)"
- Fan title: "AoT"

The existing `cleanTitle()` handles brackets and special chars, but doesn't handle
translation/romanization differences. **Possible mitigation**: Use the AniList/MAL API
(if tracked) to fetch alternate titles, and search each source with multiple title
variants. This adds API calls to the tracker service, but those are lightweight GraphQL
queries with generous rate limits.

**Challenge 2: Chapter Number Alignment Across Sources**

Different sources may number chapters differently:
- Source A: Chapter 100, 101, 102
- Source B: Chapter 100, 100.1, 100.2, 101 (including sub-chapters)
- Source C: Volume 10 Chapter 1, Volume 10 Chapter 2 (volume-based numbering)

When chapters come from different sources (hierarchy fallback), the `chapter_number`
field may not align. **Possible mitigation**: Normalize chapter numbers during sync,
or only merge chapter lists at the "full list" level (use one source's complete list
rather than mixing individual chapters from different sources).

**Challenge 3: Scanlator/Translation Quality Differences**

A user might prefer Source A for chapters 1-50 (better translation) but Source B for
chapters 51+ (faster releases). The current strategy system operates at the manga level,
not the chapter level. **Possible future extension**: Per-chapter-range source overrides,
but this dramatically increases complexity and is probably a v2 feature.

**Challenge 4: Discovery Timing for New Users**

A new user who installs the app, adds 200 manga from a backup, and has 15 extensions
installed would trigger discovery for 200 × 15 = 3,000 search calls. Even at 3 concurrent
with 2s delays, that's ~33 minutes. **Mitigations**:
- Discovery is background (WorkManager) — user doesn't wait for it
- Process in batches with increasing delays
- Show progress: "Discovering sources: 47/200 manga complete"
- Allow cancellation
- Prioritize: discover for the manga the user views first

**Challenge 5: "Confirmed Not Available" Might Change**

We negatively cache sources that don't have a series (status=NOT_FOUND). But sources add new
series over time. The 90-day expiry partially addresses this, but the user has no way to
know "Source X just added this series." **Possible mitigation**: When a user manually
searches a source for a manga and finds it, clear the negative cache entry for that pair.

**Challenge 6: Extension Source ID Stability**

Source IDs are MD5 hashes of `"name/lang/versionId"`. If an extension bumps its
`versionId`, the source ID changes. All mappings referencing the old ID become orphaned.
**Mitigation**: Listen for extension updates, detect ID changes, and migrate mappings.
The existing extension update system already handles some of this for the primary
`manga.source` field, but `source_mappings` would need the same treatment.

**Challenge 7: User Mental Model**

This feature changes how users think about manga in the app. Instead of "my manga is
from MangaDex," it becomes "my manga pulls from wherever is best." Some users may find
this confusing, especially when debugging "why did my manga switch sources?" Good UX
design is critical: clear indicators of which source served each chapter, transparent
logging of fallback events, and easy manual overrides.

**Challenge 8: Testing Surface Area**

The current test suite has exactly 1 test file (`MigratorTest`). This feature touches
the database, network layer, background jobs, and UI. Without comprehensive tests,
regressions are likely. **Mitigation**: Phase 1 must include unit tests for the matching
algorithm and integration tests for the schema migration before any behavioral changes ship.

---

## 🔀 Approach B: Lean Identity — The Middle Ground

> **Approach A** (above, Parts 1-12) is the full multi-source system: automatic
> discovery, source_mappings table, multiple resolution strategies, round-robin,
> quality selection, etc. It's powerful but complex.
>
> **Approach B** (this section) asks: what if we keep manga identity-based but stay
> on ONE source at a time, and only check alternatives when the user asks or when
> something breaks?

### B.1: Core Philosophy — "One Source, Smart Fallback"

The key insight from the Approach A analysis is that **most of the complexity comes
from managing multiple active sources simultaneously**. The source_mappings table,
resolution strategies, chapter-level source tracking, per-refresh source selection —
all of that exists to support a multi-source world.

The middle ground: **Manga is identified by canonical ID (tracker remote_id), but it
always has exactly one active source at a time**, just like today. The difference is:

1. We **record the canonical ID** so we know "this is One Piece" regardless of source
2. We **detect when the active source breaks** (returns 0 chapters, 404s, etc.)
3. When broken, we **suggest a replacement source** using the existing migration search
4. The user can **manually request** "check if other sources have more chapters"
5. We **never automatically search all sources** — the user drives alternative discovery

This avoids every single one of the "Open Challenges" from Approach A while retaining
the identity foundation that makes future enhancements possible.

### B.2: Schema Changes — Minimal

```sql
-- Migration 12.sqm (Approach B version)
-- Only 2 new columns on the existing mangas table. No new tables.

ALTER TABLE mangas ADD COLUMN canonical_id TEXT;
    -- Canonical identity string. Format: "al:21" or "mal:13" or "mu:abc123"
    -- Prefix key: al=AniList, mal=MyAnimeList, mu=MangaUpdates
    -- Derived from manga_sync.remote_id when a tracker is linked.
    -- Priority: AniList > MAL > MangaUpdates (first linked wins, user can change later)
    -- NULL for manga without tracker links (legacy behavior preserved).
    -- TEXT not INTEGER because it includes the tracker prefix for unambiguous identity.

ALTER TABLE mangas ADD COLUMN source_status INTEGER NOT NULL DEFAULT 0;
    -- 0 = HEALTHY: source is working fine (default for all existing manga)
    -- 1 = DEGRADED: source returned fewer chapters than expected (possible removal)
    -- 2 = DEAD: source returned 0 chapters or errored on last N refreshes
    -- 3 = REPLACED: user switched to a new source via suggestion
```

**That's it.** No `source_mappings` table. No `source_strategy` column. No
`resolved_source_id` on chapters. No `canonical_tracker_type` column.

**Why `canonical_id` as TEXT?** Because `"anilist:21"` is self-describing. If we
stored just `21` (an integer), we'd need a separate column to know it's an AniList
ID vs a MAL ID. The string format is unambiguous, human-readable in DB dumps, and
trivially parseable (`split(":")`).

**Why `source_status` instead of a boolean?** Because the degraded state (fewer
chapters than expected) is different from dead (zero chapters). Degraded suggests
"something changed" while dead suggests "source is down." Different UI treatments.

### B.3: How It Works — Day-to-Day

#### Normal Library Refresh (Zero Changes)

```
LibraryUpdateJob.updateManga(manga):
    source = sourceManager.get(manga.source)      // Same as today
    chapters = source.getChapterList(manga.toSManga())  // Same as today
    
    // NEW: simple health check (3 lines of logic)
    // CHAPTER_DROP_THRESHOLD = 0.7 — configurable, tune with real-world data
    previousCount = getChapterCount(manga.id)
    if chapters.isEmpty() && previousCount > 0:
        manga.source_status = DEAD
    else if chapters.size < previousCount * CHAPTER_DROP_THRESHOLD:
        manga.source_status = DEGRADED
    else:
        manga.source_status = HEALTHY
    
    syncChaptersWithSource(chapters)               // Same as today
```

**API calls: Identical to current system.** Zero additional calls. The health check
uses data we already fetched.

#### When Source Breaks (Automatic Detection + User-Driven Fix)

When `source_status` becomes `DEAD` or `DEGRADED`:

1. **Notification**: "Source [X] may have removed [Manga Title]" with action button
2. **Manga Detail Badge**: Visual indicator (⚠️) on the source chip
3. **"Find Replacement" Button**: On manga detail, triggers a **single** search using
   the existing migration engine (`SmartSourceSearchEngine`)
4. User picks from results → manga.source is updated (standard migration)

**No automatic discovery.** The user decides when and whether to look for alternatives.

#### User Requests "Check Other Sources" (On-Demand Only)

On the manga detail screen, a new action: **"Compare Sources"**

```
compareSourcesForManga(manga):
    // Uses the EXISTING migration search engine — no new code needed
    candidates = installedCatalogueSources
        .filter { it.lang in enabledLanguages }
        .filter { it.id != manga.source }  // Skip current source
    
    results = smartSearchEngine.search(candidates, manga.title)
    // results already includes: source name, title match, chapter count
    
    // Show in UI: "MangaDex: 342 chapters | MangaSee: 338 chapters | ..."
    // User can tap to switch source (same as migration)
```

**API calls: Only when the user explicitly taps "Compare Sources."** Not on refresh.
Not in background. Not automatically.

#### Canonical ID Population (Passive, Zero-Cost)

```
// When user links a tracker (already happens today):
MangaScreenModel.registerTracking(tracker, remoteId):
    // ... existing tracking logic ...
    
    // NEW: also set canonical_id if not already set
    // Prefix map: AniList→"al", MyAnimeList→"mal", MangaUpdates→"mu"
    if manga.canonical_id == null:
        val prefix = TRACKER_ID_PREFIXES[tracker.id]  // e.g. 2→"al", 1→"mal", 7→"mu"
        manga.canonical_id = "${prefix}:${remoteId}"
        updateManga(manga)
```

**API calls: Zero additional.** We're just saving a value we already have.

#### Canonical ID Use Cases (Future-Ready)

With `canonical_id` populated, we can later:
- Detect duplicate manga in library (same series from different sources)
- Auto-suggest "you already have this" when browsing
- Enable Approach A features incrementally (source discovery keyed by canonical_id)
- Export/import library by identity rather than by source URL

But none of this requires the full Approach A machinery. The canonical_id column is
a **foundation investment** that pays off whether we build Approach A or not.

### B.4: What This Doesn't Do (Intentionally)

| Capability | Approach A | Approach B | Why B Skips It |
|------------|-----------|-----------|----------------|
| Automatic multi-source discovery | ✅ Full | ❌ None | Biggest API cost and complexity |
| Automatic source failover | ✅ Silent | ⚠️ User-prompted | Avoids wrong-series risk |
| Multiple active sources | ✅ Yes | ❌ One at a time | Eliminates chapter alignment issues |
| Resolution strategies | ✅ 4 strategies | ❌ None | Single source = no strategy needed |
| Per-chapter source tracking | ✅ Yes | ❌ No | All chapters from one source |
| Negative caching | ✅ Yes | ❌ No | No discovery = no need to cache misses |
| Quality comparison | ✅ Yes | ❌ No | User picks source, not the app |
| Background bulk discovery | ✅ Yes | ❌ No | No discovery phase at all |
| Confidence scoring | ✅ Weighted | ❌ No | Reuses existing migration match UI |
| Source priority ordering | ✅ Drag/drop | ❌ No | One source = no ordering |

### B.5: Direct Comparison

#### Schema Complexity
| | Approach A | Approach B |
|--|-----------|-----------|
| New tables | 1 (`source_mappings`) | 0 |
| New columns on `mangas` | 3 | 2 |
| New columns on `chapters` | 1 | 0 |
| New indexes | 3 | 0 |
| Migration file size | ~30 lines SQL | ~5 lines SQL |

#### Code Complexity
| | Approach A | Approach B |
|--|-----------|-----------|
| New domain models | 2 (`SourceMapping`, `SourceStrategy`) | 0 (just an enum for status) |
| New use cases | 4 | 0 (reuses existing migration search) |
| New repository interfaces | 1 (`SourceMappingRepository`) | 0 |
| Modified files | ~15 | ~5 |
| New background jobs | 1 (`SourceDiscoveryJob`) | 0 |
| Implementation phases | 5 (10 weeks) | 2 (3-4 weeks) |

#### API Cost
| | Approach A | Approach B |
|--|-----------|-----------|
| One-time discovery cost | ~1000 calls (100 manga × 10 sources) | 0 |
| Per-refresh cost | Same as current | Same as current |
| On user action | None (already discovered) | ~10 calls (search 10 sources for 1 manga) |
| Worst case (upgrade day) | 1000+ calls/user | 0 calls |

#### Risk Profile
| | Approach A | Approach B |
|--|-----------|-----------|
| False positive match risk | Medium (fuzzy matching) | None (user confirms) |
| Wrong series chapters | Possible (auto-matched) | Impossible (user picks) |
| Extension server spike | Yes (discovery phase) | No |
| Data migration risk | Higher (more schema) | Lower (2 columns) |
| Behavioral change surface | Large (refresh path) | Minimal (health check only) |

### B.6: Avoiding Approach A's Pitfalls

Each of the 8 "Open Challenges" from Approach A's Part 12, and how Approach B handles them:

**Challenge 1: Tracker ID Coverage Gap**
- Approach A: Must handle sources that don't embed tracker IDs → fuzzy matching fallback
- **Approach B: Not a problem.** We don't search sources automatically. Canonical ID is
  only used for identity/dedup, not for discovery. If a manga has no tracker, it works
  exactly like today.

**Challenge 2: Chapter Number Alignment Across Sources**
- Approach A: Different sources number chapters differently → merge conflicts
- **Approach B: Not a problem.** One source at a time = one numbering scheme. When user
  switches sources (migration), the existing migration code handles this.

**Challenge 3: Scanlator/Translation Quality Differences**
- Approach A: Can't express "Source A for chapters 1-50, Source B for 51+"
- **Approach B: Same limitation, but explicit.** User picks one source. If they want to
  switch, they migrate. No hidden source-mixing that could surprise them.

**Challenge 4: Discovery Timing for New Users**
- Approach A: 200 manga × 15 sources = 3,000 search calls on first run
- **Approach B: Zero calls on first run.** Everything works exactly as before. User can
  compare sources one manga at a time, when they choose to.

**Challenge 5: "Confirmed Not Available" Might Change**
- Approach A: Need negative caching with expiry logic
- **Approach B: Not a problem.** No caching of search results. Each "Compare Sources"
  request is fresh.

**Challenge 6: Extension Source ID Stability**
- Approach A: Source ID changes orphan mappings → need migration logic
- **Approach B: Not a problem.** No mappings table to orphan. The existing extension
  update system already handles `manga.source` ID changes.

**Challenge 7: User Mental Model**
- Approach A: "My manga pulls from wherever" — potentially confusing
- **Approach B: Crystal clear.** "My manga is from MangaDex. It broke. I switched to
  MangaSee." Users always know exactly which source is active.

**Challenge 8: Testing Surface Area**
- Approach A: Database + network + background jobs + UI = large test surface
- **Approach B: Tiny test surface.** Health check is 3 lines of logic. Canonical ID
  is a string column. "Compare Sources" reuses existing migration search.

### B.7: Implementation Plan — Two Phases

#### Phase 1: Schema + Health Detection (Week 1-2)

**Files to modify:**

| File | Change | Lines |
|------|--------|-------|
| `data/.../migrations/12.sqm` | 2 ALTER TABLE statements | ~5 |
| `data/.../data/mangas.sq` | Add 2 columns to CREATE TABLE + update insert/update queries | ~10 |
| `domain/.../manga/model/Manga.kt` | Add `canonicalId: String?` and `sourceStatus: Int` | ~4 |
| `data/.../manga/MangaMapper.kt` | Map new columns | ~2 |
| `data/.../manga/MangaRepositoryImpl.kt` | Include new columns in CRUD | ~5 |
| `app/.../data/library/LibraryUpdateJob.kt` | Health check after chapter fetch | ~10 |

**Total: ~36 lines of actual code changes across 6 files.**

Deliverable: Source health is tracked. Canonical ID column exists. Zero behavioral changes
for users (health status is tracked but not yet surfaced in UI).

#### Phase 2: UI + Canonical ID Population (Week 3-4)

**Files to modify:**

| File | Change | Lines |
|------|--------|-------|
| `app/.../presentation/manga/MangaScreen.kt` | Show health badge on source chip | ~15 |
| `app/.../ui/manga/MangaScreenModel.kt` | Populate canonical_id on tracker link; "Compare Sources" action | ~20 |
| `app/.../presentation/manga/components/MangaInfoHeader.kt` | Source health indicator | ~10 |
| `app/.../data/library/LibraryUpdateJob.kt` | Notification for DEAD/DEGRADED manga | ~15 |

**Total: ~60 lines of actual code changes across 4 files.**

Deliverable: Users see when sources break. Can compare sources on demand. Canonical ID
auto-populated when trackers are linked. Full feature complete.

### B.8: Future Upgrade Path — B → A

Approach B is explicitly designed as a **foundation** for Approach A. If we ship B and
later decide we want A's features:

1. **Canonical ID is already there** — No schema change needed for identity
2. **Health detection is already there** — The `source_status` column tells A when to
   trigger auto-discovery
3. **source_mappings table can be added later** — Migration 13.sqm adds the table;
   existing manga with `canonical_id` can be backfill-discovered
4. **No data loss** — B's schema is a strict subset of A's. Adding A's columns/tables
   is purely additive

The upgrade path from B to A is:
```
B (canonical_id + source_status)
  + source_mappings table
  + source_strategy column
  + resolved_source_id on chapters
  + DiscoverSourcesForManga use case
  + ResolveChapterSource use case
  = A
```

Each step can be shipped independently. **Approach B is Phase 1 of Approach A**, just
with a different stopping point that might be good enough for most users.

---

## ✅ Chosen Approach: C — Curated Search with Lean Identity

> **Status: IMPLEMENTING** — This is the selected design, combining Approach B's lean
> backend with a curated search UX that makes finding manga feel fundamentally better.

### C.1: The Core Insight

The current search experience is confusing: a user types "One Piece" and gets 5+ sources
each showing multiple results of varying quality. The user has no idea which to pick.

**Approach C** inverts this: search hits **authoritative metadata sources** (AniList, MAL)
first to identify *what* the user wants, then recommends the **best chapter source** based
on coverage metrics. The user experience becomes:

1. **Search**: "What do you want to read?" → Results from AniList/MAL (one canonical entry
   per series, with cover, description, score, status)
2. **Select**: User picks "One Piece" → App shows the authoritative metadata
3. **Source**: App recommends the best available chapter source based on chapter count and
   availability, with alternatives visible but secondary
4. **Add**: User adds to library with one tap

This is fundamentally different from the current "search every extension and see what sticks"
approach. It reduces API calls (only hit authoritative sources for search), improves result
quality (no duplicates, no mystery sources), and feels more like a curated catalog than a
raw search engine.

### C.2: Two-Tier Architecture

```
┌─────────────────────────────────────────────────────┐
│  Tier 1: AUTHORITATIVE SEARCH (metadata identity)   │
│  AniList, MAL, MangaUpdates                         │
│  Used for: Search, metadata, covers, descriptions   │
│  When: User searches for new manga                  │
│  Cost: 1 API call per search (already rate-limited) │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│  Tier 2: CHAPTER SOURCES (reading content)          │
│  Extension sources (MangaDex, MangaSee, etc.)       │
│  Used for: Chapter lists, page images               │
│  When: User selects manga AND asks for chapters     │
│  Cost: Search only selected extensions, not all     │
└─────────────────────────────────────────────────────┘
```

### C.3: Schema — Same as Approach B (Minimal)

```sql
-- Migration 12.sqm
ALTER TABLE mangas ADD COLUMN canonical_id TEXT;
    -- Format: "al:21" or "mal:13" or "mu:abc123"
    -- Set when tracker is linked. NULL = legacy (no tracker linked)

ALTER TABLE mangas ADD COLUMN source_status INTEGER NOT NULL DEFAULT 0;
    -- 0 = HEALTHY, 1 = DEGRADED, 2 = DEAD, 3 = REPLACED
```

**No new tables. No changes to chapters table. 2 columns on mangas.**

### C.4: Implementation — What We Build Now

#### 1. Schema + Domain Model (this PR)

- Migration 12.sqm: 2 ALTER TABLE statements
- Manga.kt: Add `canonicalId: String?` and `sourceStatus: Int` fields
- MangaUpdate.kt: Add corresponding update fields
- MangaMapper.kt: Map new columns
- MangaRepositoryImpl.kt: Include in partialUpdate
- SourceStatus enum: HEALTHY (0), DEGRADED (1), DEAD (2), REPLACED (3)

#### 2. Source Health Detection (this PR)

- LibraryUpdateJob: After fetching chapters, compare count to previous
- If chapters.isEmpty() && previousCount > 0 → DEAD
- If chapters.size < previousCount * 0.7 → DEGRADED
- Otherwise → HEALTHY

#### 3. Canonical ID Population (this PR)

- When user links a tracker: auto-set canonical_id from tracker remote_id
- Priority: AniList (al:) > MAL (mal:) > MangaUpdates (mu:)
- Only set if canonical_id is currently NULL (first-linked wins)

#### Future enhancements (not this PR):

- Curated search UI (search AniList/MAL first, then find chapter sources)
- Source health badges on manga detail screen
- "Compare Sources" / "Find Replacement" actions
- Duplicate detection via canonical_id
- Smart migration suggestions when source is DEAD

### C.5: Why This Design Wins

| Dimension | Current | Approach A | Approach B | **Approach C** |
|-----------|---------|-----------|-----------|---------------|
| Search UX | Confusing (N sources × M results) | Same search, better failover | Same search, health monitor | **Curated (1 result per series)** |
| API cost | Hits all sources | Spike on discovery | Zero increase | **Zero increase now; search only authoritative later** |
| Schema complexity | — | 1 new table, 4 new columns | 2 columns | **2 columns** (same as B) |
| Implementation risk | — | High | Low | **Low** (same as B) |
| "Feels better" | No | No (same UX) | Slightly (health badges) | **Yes** (curated catalog) |
| Future potential | — | Already maximal | Foundation for A | **Foundation for A + curated search** |

### C.6: How Curated Search Will Work (Future)

```
User types "One Piece" in search bar

STEP 1: Hit AniList API (already integrated via Tracker)
  → Returns: One Piece (id: 21, cover, description, 1088 chapters, score: 87%)
  → Also returns: One Piece Party, One Piece: Chin Piece (related titles)
  → One result per series, no source confusion

STEP 2: User taps "One Piece"
  → Shows AniList metadata (cover, description, status, score)
  → Below: "Available from:" section shows installed extension sources
  → Each source shows: chapter count, last update, language
  → Recommended source highlighted (highest chapter count + active)

STEP 3: User taps "Add to Library"
  → Manga created with source = recommended extension source
  → canonical_id = "al:21" (from AniList selection)
  → metadataSource = AniList source ID (for ongoing metadata)
  → Tracker auto-linked (since we already know the AniList ID)
```

**Key optimization**: During Step 1, we only hit ONE API (AniList). We don't hit any
extension sources until the user actually selects a manga. And even then, we only search
the installed extensions for that specific title — not all of them for every search term.

---

## 📊 Decision Summary: Approach A vs Approach B vs C

### Side-by-Side Verdict

| Dimension | Approach A (Full Multi-Source) | Approach B (Lean Identity) | **Approach C (Curated Search)** | Winner |
|-----------|------------------------------|--------------------------|-------------------------------|--------|
| **User value** | Automatic failover, zero-touch | Manual but reliable | **Curated catalog feel** | **C** |
| **Implementation risk** | High (15 files, 10 weeks) | Low (6 files, 4 weeks) | **Low (same schema as B)** | B/C tie |
| **API compliance** | Good steady-state, spike on discovery | Zero additional calls | **Zero now; authoritative-only later** | **C** |
| **Correctness** | Risk of wrong-series match | User always confirms | **User confirms + canonical ID** | B/C tie |
| **Complexity** | New table + 4 use cases + background job | 2 columns + health check | **2 columns + health check** | B/C tie |
| **Test surface** | Large (DB + network + bg + UI) | Tiny (3 lines of logic) | **Tiny (same as B)** | B/C tie |
| **Backwards compat** | Safe (opt-in) | Safe (opt-in) | **Safe (opt-in)** | Tie |
| **Future extensibility** | Already maximal | Clean upgrade to A | **Foundation for A + curated search** | **C** |
| **Time to ship** | 10 weeks | 3-4 weeks | **Phase 1: 2 weeks (schema)** | **C** |
| **Server impact** | Discovery spike risk | Zero impact | **Zero impact; reduces future** | **C** |
| **Search UX** | Same as current | Same as current | **Curated, catalog-like** | **C** |

### Recommendation — FINAL

**Ship Approach C.** It is Approach B's schema with a clear UX vision for curated search.

Phase 1 (this PR): Schema foundation + health detection + canonical ID population.
Phase 2 (next PR): Source health UI badges + "Compare Sources" action.
Phase 3 (future): Curated search UI (AniList-first search, source recommendation).
Phase ∞ (if needed): Layer Approach A's auto-discovery on top.

### Pros of the Overall Design (Both Approaches)

1. **Canonical identity is the right foundation** — Whether A or B, decoupling manga
   identity from source is the correct architectural direction. It enables dedup,
   smarter backup/restore, and future features.

2. **Health monitoring has standalone value** — Even without source switching, knowing
   that a source has degraded or died is useful information for users.

3. **Reuses existing code** — Both approaches build on `SmartSourceSearchEngine`,
   `manga_sync.remote_id`, and the existing migration flow. No reinventing the wheel.

4. **Extension-agnostic** — Neither approach requires changes to the source API or
   extensions. Everything works with existing extensions as-is.

5. **Incremental delivery** — Both can be shipped in phases with independently testable
   milestones.

### Cons of the Overall Design (Both Approaches)

1. **Canonical ID requires tracker usage** — The best identity comes from AniList/MAL
   remote IDs. Manga without trackers get `canonical_id = NULL`, which limits dedup and
   identity features for those entries.

2. **Source health heuristics are imperfect** — "Chapter count dropped 30%" could mean
   the source removed content, or it could mean the source fixed duplicate entries.
   False positives are possible. Threshold tuning will require real-world data.

3. **The "Compare Sources" UX needs careful design** — Showing "MangaDex: 342 chapters |
   MangaSee: 338 chapters" is only useful if chapter counts are comparable. Different
   sources may include/exclude bonus chapters, oneshots, etc. Raw counts can mislead.

4. **Neither approach handles the "best translation" problem** — A user who wants
   English chapters 1-50 from Source A (better quality) and 51+ from Source B (faster
   releases) can't express this in either approach. This remains a manual process.

5. **Schema migrations are one-way** — Once we add `canonical_id` and `source_status`
   columns, they're permanent. If the feature is abandoned, the columns remain as dead
   weight in the schema. (Low risk — these are tiny columns with sensible defaults.)

### Open Challenges That Remain

1. **Canonical ID format standardization** — The `"al:21"` format uses short prefixes
   (`al`=AniList, `mal`=MAL, `mu`=MangaUpdates) mapped from tracker IDs. Priority:
   AniList > MAL > MangaUpdates. If a manga is tracked on both AniList and MAL, the
   canonical_id uses AniList. Changing the canonical tracker later needs a UI affordance.

2. **Health check threshold tuning** — The 70% threshold for DEGRADED status is a guess.
   Too aggressive = false alarms on sources that legitimately remove duplicate chapters.
   Too lenient = missed removals. Needs real-world telemetry or user feedback to calibrate.

3. **"Compare Sources" result presentation** — How do we show that MangaDex has "342
   chapters" while the user's current source has "338 chapters"? Is +4 chapters meaningful
   or noise? Need to surface which specific chapters are missing/extra, not just counts.

4. **Handling source migration of read progress** — When a user switches sources via
   "Find Replacement," their read progress (which chapters are marked read, bookmarks,
   last page read) needs to carry over. The existing migration system handles some of
   this, but chapter URL changes can break the mapping. This is an existing problem
   that Approach B inherits rather than solves.

5. **Tracker-less manga identity** — Without a tracker, `canonical_id` is NULL. We could
   generate a synthetic ID from normalized title + author, but that's fuzzy and could
   collide. Alternative: let users manually tag manga with the same identity, but that's
   complex UI for a niche case.

6. **When to show the health notification** — Showing "Source X may have removed Manga Y"
   on every refresh where status changes could be noisy. Need debouncing: only notify if
   the status has been DEAD for N consecutive refreshes (e.g., 3), to avoid false alarms
   from temporary source outages.

7. **Extension uninstall handling** — If a user uninstalls an extension, all manga from
   that source become DEAD. This is a known user action, not a surprise. Should we suppress
   the "source broken" notification for manga whose source was deliberately uninstalled?
   Probably yes — detect extension removal and mark those manga differently (e.g., a new
   status `UNINSTALLED = 4`).

---

## 📖 Reader Improvements

### Page Turn Animations
- Add configurable page transition effects: **slide**, **curl**, **fade**, **flip**
- Use `MotionTokens` for consistent timing
- Implement as a `PageTransition` sealed interface with `Compose` animation specs
- Settings: None (instant), Slide, Curl (skeuomorphic), Fade, Flip 3D
- **Complexity**: Medium | **Priority**: High

### Thumbnail Page Scrubber
- While holding and dragging on the page timeline/seekbar, show a **floating thumbnail preview** of the target page
- Preload low-res thumbnails for all pages in current chapter
- Use Coil's thumbnail/resize transformations for memory efficiency
- Similar to video player scrubbing UX (YouTube, VLC)
- Show page number overlay alongside the thumbnail
- **Complexity**: Medium | **Priority**: High

### More Reader Themes
- **Sepia** — Warm parchment tone for comfortable extended reading
- **Night Blue** — Deep navy with subtle blue-shift, easier on eyes than pure black
- **Solarized Dark/Light** — Popular dev-friendly color schemes
- **Custom Tint** — User picks any overlay color + opacity
- **Auto** — Switch based on time-of-day or ambient light sensor
- Implementation: Reader background color + optional color matrix filter on pages
- **Complexity**: Low-Medium | **Priority**: Medium

### Reader Polish
- **Double-tap smart zoom**: Zoom to tapped panel region using content-aware detection
- **Immersive chapter transitions**: Smooth loading shimmer instead of hard cuts between chapters
- **Page gap indicator**: Subtle visual separator between pages in webtoon mode
- **Reading position memory**: Remember exact scroll position within long webtoon pages
- **Complexity**: Mixed | **Priority**: Medium

---

## 🎨 UI / Visual Design

### Cover-Based Dynamic Color Extraction
- Extract dominant colors from manga cover thumbnails using Palette API
- Tint the manga detail screen header/toolbar with extracted colors
- Subtle gradient overlay on cover images using extracted palette
- Cache extracted palettes alongside cover cache for performance
- Already have Coil for image loading — add Palette integration
- **Complexity**: Medium | **Priority**: High

### Shared Element Transitions
- Animate cover image from library grid → manga detail screen
- Use Compose `SharedTransitionLayout` and `animatedVisibilityScope`
- Cover morphs position/size/shape smoothly between screens
- Requires Voyager navigation integration or migration to Navigation Compose
- **Complexity**: High (navigation framework coupling) | **Priority**: High

### Additional Visual Polish
- **Animated navigation indicators**: Sliding pill between nav items (M3 Expressive)
- **Staggered grid layout**: Pinterest-style for library (natural aspect ratios)
- **Reading progress ring**: Circular indicator on cover showing % chapters read
- **Custom app icon packs**: Themed icons matching each color scheme
- **Glassmorphism overlays**: Blur/frosted-glass on reader overlays and bottom sheets
- **Complexity**: Mixed | **Priority**: Medium

---

## 🧭 UX / Interaction Design

### Smart Search with Suggestions
- Show **recent searches** in dropdown when search field is focused
- Display **trending/popular** titles from installed sources
- **Auto-complete** based on local library titles + source catalog
- **Fuzzy matching**: Tolerate typos using edit distance
- Tag-based filtering: Type "genre:action" or "author:Oda" for structured search
- **Complexity**: Medium | **Priority**: High

### Smart Collections / Dynamic Lists
- User-created collections with **rule-based auto-population**:
  - "Unread from tracked sources"
  - "Completed this month" 
  - "Downloaded but not started"
  - "Highly rated (score > 8)" via tracker integration
  - "Updating weekly" based on fetch interval
- Combine conditions with AND/OR logic
- Pin collections to library tabs alongside categories
- **Complexity**: Medium-High | **Priority**: High

### Quick Actions & Gestures
- **Long-press radial menu**: Mark read, download, share, track — accessible without entering detail
- **Swipe actions on list items**: Swipe chapter for quick download/mark read
- **Pull-down quick settings**: Pull down on library for quick filter toggles
- **Haptic feedback tokens**: Light (selection), medium (confirmation), heavy (destructive)
- **Complexity**: Medium | **Priority**: Medium

### Batch Operations
- **Floating action bar** during multi-select showing available actions
- **Select all in category**, **select by filter** (all unread, all downloaded)
- **Batch migrate**: Select multiple manga → auto-match → migrate in batch
- **Complexity**: Medium | **Priority**: Medium

### Onboarding Improvements
- Animated step-by-step with progressive disclosure
- Quick-start wizard: pick theme → add first source → browse → add first manga
- Contextual tips on first use of each feature
- **Complexity**: Medium | **Priority**: Low

### Adaptive Chapter Sorting
- Learn per-manga sort preference (newest-first vs oldest-first)
- Default: oldest-first for new series, newest-first for caught-up series
- Remember user override per-manga
- **Complexity**: Low | **Priority**: Low

---

## ⚡ Performance & Technology (API-Respectful)

### Baseline Profiles
- Generate startup profiles for critical paths: library scroll, reader page flip
- Use Macrobenchmark module (already exists in repo) to measure and optimize
- Target: 30%+ faster cold start, smoother initial scroll
- **Complexity**: Medium | **Priority**: High

### Compose Stability Optimization
- Run Compose compiler reports to find unnecessary recompositions
- Annotate data classes with `@Immutable`/`@Stable` where appropriate
- Convert hot-path lambdas to remembered instances
- Target: Eliminate jank in library grid scrolling
- **Complexity**: Medium | **Priority**: High

### Intelligent Prefetch Pipeline
- Prefetch next 2-3 pages while reading current page
- Prefetch next chapter's first pages when approaching chapter end
- **Respect API costs**: Configurable prefetch depth, honor rate limits
- Use priority queue: current page > next page > prefetch pages
- Cancel prefetch on rapid page navigation (don't waste bandwidth)
- **Complexity**: Medium | **Priority**: High

### Image Format Optimization
- Prefer AVIF/WebP decoding for cache storage (30-50% smaller)
- Re-encode downloaded pages to WebP before writing to disk cache
- Configurable: original format vs re-encoded (trade CPU for storage)
- **Complexity**: Low-Medium | **Priority**: Medium

### Reactive Database Queries
- Migrate from manual refresh to SQLDelight Flow-based queries
- UI automatically updates when underlying data changes
- Eliminate `forceRefresh()` patterns throughout codebase
- **Complexity**: Medium (incremental migration) | **Priority**: Medium

### Smart Background Sync
- Adaptive WorkManager scheduling based on manga update patterns
- Manga that updates daily → check daily; monthly → check weekly
- **Respect server costs**: Stagger requests, honor rate limits, exponential backoff
- Low battery / metered network → defer non-critical updates
- **Complexity**: Medium | **Priority**: Medium

### Memory-Mapped Image Cache
- Use memory-mapped files for large image caches to reduce GC pressure
- Particularly beneficial for webtoon reader with many large images
- **Complexity**: High | **Priority**: Low

### Startup Optimization
- Profile with Macrobenchmark, identify and defer non-critical initialization
- Lazy-load extension manager, tracker services, telemetry
- **Complexity**: Medium | **Priority**: Medium

---

## 🔧 Architecture & Code Quality

### Design Token Documentation
- Auto-generate living style guide from MotionTokens, ShapeTokens, Typography, Color
- Compose Preview catalog showing every token in light/dark/AMOLED modes
- **Complexity**: Low | **Priority**: Medium

### Compose Preview Catalog
- `@Preview` for every reusable component in presentation-core
- Preview variants: light/dark, compact/expanded, empty/populated states
- Use Paparazzi or Roborazzi for automated screenshot regression tests
- **Complexity**: Medium | **Priority**: Medium

### Module Boundary Enforcement
- Strict dependency rules: domain never imports from app, presentation-core never imports from data
- Use Gradle module dependency linting or custom detekt rules
- **Complexity**: Low | **Priority**: High

### Snapshot Testing
- Add Paparazzi/Roborazzi screenshot tests for key screens
- CI catches visual regressions before merge
- Start with: library grid, manga detail, reader, settings
- **Complexity**: Medium | **Priority**: Medium

### Dependency Injection Migration
- Current: Injekt (lightweight but dated)
- Consider: Koin (Kotlin-native, simpler) or Hilt (Google-backed, compile-time safe)
- Migration can be incremental — introduce new DI alongside Injekt
- **Complexity**: Very High | **Priority**: Low (works fine currently)

### Coroutine Structured Concurrency Audit
- Ensure all background work uses proper scoping and cancellation
- Audit `GlobalScope` usage, replace with structured scopes
- **Complexity**: Medium | **Priority**: Medium

### Accessibility Audit
- TalkBack/Switch Access testing for all screens
- Add missing `contentDescription` on all interactive elements
- Focus ordering for complex layouts (manga detail, reader controls)
- **Complexity**: Medium | **Priority**: Medium

### Strict Kotlin API Mode
- Enable explicit API mode for library modules (domain, source-api, presentation-core)
- Forces all public APIs to have explicit visibility modifiers
- Prevents accidental API surface expansion
- **Complexity**: Low | **Priority**: Medium

---

## 🌟 Additional Features

### Reading Statistics Dashboard
- Charts: reading velocity, genre breakdown, daily/weekly reading patterns
- Streak tracking with optional notifications
- Monthly/yearly reading summaries
- Data sourced from existing history table
- **Complexity**: Medium | **Priority**: Medium

### Community Recommendations (Optional/Opt-In)
- "Users who read X also enjoyed Y" based on anonymized library patterns
- Completely opt-in, privacy-first (differential privacy or k-anonymity)
- Could integrate with AniList/MAL recommendation APIs as simpler alternative
- **Complexity**: Very High | **Priority**: Low

### Export/Share Reading List
- Export library as shareable link, image card, or structured data (JSON/CSV)
- "Share collection" with curated picks and notes
- Import from shared lists
- **Complexity**: Medium | **Priority**: Low

### Offline Mode Indicator
- Clear visual badge/icon on manga and chapters showing offline availability
- "Available offline" filter in library
- Estimated storage usage per manga in detail screen
- **Complexity**: Low | **Priority**: Medium

### Theme Scheduling
- Auto-switch light↔dark at user-defined times or sunrise/sunset
- Per-time-slot theme selection (e.g., Lavender during day, Midnight Dusk at night)
- **Complexity**: Low | **Priority**: Low

---

## 📊 Priority Matrix

### Immediate (Next Sprint)
| Feature | Complexity | Impact |
|---------|-----------|--------|
| Baseline Profiles | Medium | High |
| Compose Stability Audit | Medium | High |
| Cover-Based Color Extraction | Medium | High |
| More Reader Themes | Low-Medium | High |
| Thumbnail Page Scrubber | Medium | High |

### Near-Term (Next Quarter)
| Feature | Complexity | Impact |
|---------|-----------|--------|
| Page Turn Animations | Medium | High |
| Smart Search Suggestions | Medium | High |
| Smart Collections | Medium-High | High |
| Shared Element Transitions | High | High |
| Unified Catalog — Phase 1 (Foundation) | High | Transformative |

### Medium-Term (3-6 Months)
| Feature | Complexity | Impact |
|---------|-----------|--------|
| Unified Catalog — Phase 2-3 (Auto-Match + Resolution) | Very High | Transformative |
| Intelligent Prefetch Pipeline | Medium | High |
| Snapshot Testing | Medium | Medium |
| Reading Statistics Dashboard | Medium | Medium |

### Long-Term (6+ Months)
| Feature | Complexity | Impact |
|---------|-----------|--------|
| Unified Catalog — Phase 4-5 (UI + Community) | Very High | Transformative |
| Community Recommendations | Very High | Medium |
| DI Migration | Very High | Medium |

---

## 🏗️ Technical Debt to Address

- [ ] Replace hardcoded animation durations with `MotionTokens` throughout app module
- [ ] Replace hardcoded padding values with `MaterialTheme.padding` tokens  
- [ ] Replace hardcoded shape radii with `ShapeTokens` / `MaterialTheme.shapes`
- [ ] Audit and add `@Immutable`/`@Stable` annotations to data classes used in Compose
- [ ] Increase test coverage (currently only `MigratorTest` exists)
- [ ] Add KDoc to all public APIs in domain and source-api modules
- [ ] Migrate remaining uses of `titleSmall` to `sectionLabel` for section headers
