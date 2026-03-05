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
