# Mihon Fork — Roadmap & Next Actions

> **Last updated:** 2026-03-05
> **Branch:** `copilot/improve-design-and-performance`

---

## Current State Summary

### ✅ Fully Implemented

| Feature | Key Files | Tests |
|---------|-----------|-------|
| **Database schema** — `canonical_id`, `source_status`, `alternative_titles` columns on `mangas` table | Migrations 12.sqm, 13.sqm, 14.sqm | Schema validated via build |
| **Canonical ID auto-population** — Sets `canonical_id` from tracker remote IDs on first tracker bind (MAL → `mal:`, AniList → `al:`, MangaUpdates → `mu:`) | `AddTracks.setCanonicalIdIfAbsent()` | 7 unit tests |
| **Alternative titles pipeline** — AniList romaji/english/native/synonyms merged into manga, case-insensitive dedup | `AddTracks.mergeAlternativeTitles()`, `ALSearchItem.buildAlternativeTitles()` | 8 unit tests |
| **Tiered search engine** — 4-tier migration search with cross-title evaluation, dedup, near-match tracking | `BaseSmartSearchEngine.multiTitleSearch()`, `MigrationListScreenModel.searchSource()` | 16 unit tests |
| **Canonical ID lookup** — Zero-API-call local DB match via partial index | `GetFavoritesByCanonicalId`, `mangas.sq:getFavoritesByCanonicalId`, `14.sqm` index | Tested via integration |
| **Source health detection** — Automatic HEALTHY/DEGRADED/DEAD classification during library updates | `LibraryUpdateJob.kt:443-459` | Manual |
| **Source health UI** — Warning banner on manga detail screen for DEGRADED/DEAD sources | `SourceHealthBanner.kt`, `MangaScreen.kt` | Visual |
| **Design token system** — Padding, Shape, Motion, Typography, Color tokens with adoption in 10+ components | `Constants.kt`, `Motion.kt`, `Shapes.kt`, `Typography.kt`, `Color.kt` | N/A |

### ⚠️ Partially Implemented

| Feature | What's Done | What's Missing |
|---------|-------------|----------------|
| **Design token adoption** | Tokens defined; used in Pill, ListGroupHeader, SectionCard, CollapsibleBox, AdaptiveSheet, InfoScreen, LazyColumnWithAction, LinkIcon, SettingsItems, VerticalFastScroller | ~4 components still use hardcoded dp/sp values (Badges.kt, Pill.kt inner padding, Tabs.kt, NavigationBar/Rail) |

### ❌ Not Yet Implemented (Documented in BRAINSTORM.md)

| Feature | Brainstorm Section | Complexity |
|---------|-------------------|------------|
| **Source health recovery** — Auto-reset to HEALTHY when chapter count recovers | C.5 | Low |
| **Bulk migration prompt** — Suggest migration when source is DEAD for tracked manga | C.6 | Medium |
| **source_mappings table** — Full multi-source discovery (Approach A) | Parts 1-12 | High |
| **Source discovery protocol** — Automated cross-source search with confidence scoring | Part 2 | High |
| **Chapter resolution strategies** — HIERARCHY, ROUND_ROBIN, QUALITY strategies | Part 3 | High |

---

## Technical Debt

### 1. Hardcoded Design Values → Should Use Tokens

| Component | File | Hardcoded Values | Status |
|-----------|------|------------------|--------|
| `Badges.kt` | `presentation-core/.../Badges.kt:50,95` | `3.dp` horizontal, `1.dp` vertical | Kept — intentionally tight for badge styling |
| `Pill.kt` | `presentation-core/.../Pill.kt:35` | `6.dp, 1.dp` | Kept — custom component-specific padding |
| ~~`LazyColumnWithAction.kt`~~ | ~~`presentation-core/.../LazyColumnWithAction.kt:41`~~ | ~~`16.dp, 8.dp`~~ | ✅ Fixed — Padding.medium, Padding.small |
| ~~`SettingsItems.kt`~~ | ~~`presentation-core/.../SettingsItems.kt:391,449`~~ | ~~`4.dp`, `24.dp`~~ | ✅ Fixed — Padding.extraSmall, Padding.large |
| ~~`VerticalFastScroller.kt`~~ | ~~`presentation-core/.../VerticalFastScroller.kt`~~ | ~~`8.dp`~~ | ✅ Fixed — Padding.small (thumb dimensions kept component-specific) |
| ~~`LinkIcon.kt`~~ | ~~`presentation-core/.../LinkIcon.kt:22`~~ | ~~`4.dp`~~ | ✅ Fixed — Padding.extraSmall |
| `Tabs.kt` | `presentation-core/.../material/Tabs.kt:29` | `10.sp` | Pending — needs typography token |
| `NavigationBar.kt` | `presentation-core/.../material/NavigationBar.kt:43` | `80.dp` | Pending — needs component-specific token |
| `NavigationRail.kt` | `presentation-core/.../material/NavigationRail.kt` | `80.dp` | Pending — needs component-specific token |
| ~~`CircularProgressIndicator.kt`~~ | ~~`presentation-core/.../CircularProgressIndicator.kt`~~ | ~~`tween(2000)`~~ | ✅ Fixed — ROTATION_DURATION_MS constant |

### 2. Missing Test Coverage

| Area | Current | Needed |
|------|---------|--------|
| ~~`AddTracks.setCanonicalIdIfAbsent()`~~ | ~~No unit tests~~ | ✅ 7 tests added |
| ~~`AddTracks.mergeAlternativeTitles()`~~ | ~~No unit tests~~ | ✅ 8 tests added |
| `LibraryUpdateJob` health detection | No unit tests | Test: HEALTHY→DEGRADED threshold, DEAD on empty chapters, recovery |
| `GetFavoritesByCanonicalId` | No unit tests | Test: exclude self, match on canonical_id, empty results |
| Design tokens | No tests | Snapshot tests for token values to prevent regression |

### 3. Code-Level Issues

- **`AddTracks.kt:52`** — Type check `item is TrackSearch` only works when caller passes `TrackSearch` instance; `Track` instances from general tracker bind paths won't trigger alt title merge. Consider extracting alt titles from the tracker search result before the bind.
- **`MigrationListScreenModel.kt`** — `findByCanonicalId()` returns first match per source; doesn't consider confidence scoring or prefer certain tracker prefixes.
- **Pipe-separated `alternative_titles`** — Stored as `TEXT` with `|` delimiter. If a title contains `|` character, it breaks parsing. Consider JSON array or different delimiter.

---

## Next Actions (Prioritized)

### Phase 1: Quick Wins (Low Effort, High Value) — ✅ COMPLETE

#### 1.1 Source Health UI Indicator ✅
**Done:** Added `SourceHealthBanner` composable with DEGRADED (tertiary) and DEAD (error) color scheme. Wired into both small and large manga detail layouts between action row and description. Added `source_health_degraded` and `source_health_dead` string resources.

#### 1.2 Migrate Remaining Hardcoded Values to Tokens ✅
**Done:** Migrated 5 files: LazyColumnWithAction (→Padding.medium/small), LinkIcon (→Padding.extraSmall), SettingsItems (→Padding.extraSmall/large), VerticalFastScroller (→Padding.small), CircularProgressIndicator (→ROTATION_DURATION_MS constant). Remaining: Badges.kt (3.dp is intentionally tight), Pill.kt (6.dp/1.dp custom), Tabs.kt (10.sp label), NavigationBar/Rail (80.dp — needs component-specific token).

#### 1.3 Unit Tests for AddTracks Pipeline ✅
**Done:** Added 15 tests in `AddTracksTest.kt`: 7 for `setCanonicalIdIfAbsent()` (prefix mapping, first-wins, skip zero/negative/unknown tracker), 8 for `mergeAlternativeTitles()` (add, dedup, case-insensitive, blank filter, preserve existing). Changed method visibility to `internal` for testability.

### Phase 2: Feature Completion (Medium Effort)

#### 2.1 Source Health Recovery Logic
**What:** When a previously DEGRADED/DEAD source returns to normal chapter count, auto-reset to HEALTHY.
**Why:** Sources have temporary outages; permanent DEAD marking causes false alarms.
**Where:** `LibraryUpdateJob.kt` — the existing health detection code already handles this via the `else -> SourceStatus.HEALTHY` branch, but verify it works correctly when chapters recover.
**Effort:** ~1 hour (verify + test)

#### 2.2 Bulk Migration Suggestion
**What:** When source is DEAD for 3+ consecutive updates, prompt user to migrate affected manga.
**Why:** Automates the discovery of migration candidates instead of manual checking.
**Where:** New notification/dialog triggered from `LibraryUpdateJob`.
**Effort:** ~4-6 hours

#### 2.3 Alternative Title Delimiter Safety
**What:** Switch from pipe-separated `TEXT` to JSON array storage, or escape `|` in titles.
**Why:** Titles containing `|` will corrupt the alt titles list.
**Where:** Migration 15.sqm + `MangaMapper.kt` + `MangaUpdate.kt` serialization.
**Effort:** ~3-4 hours

### Phase 3: Advanced Features (High Effort)

#### 3.1 Source Health History
**What:** Track status transitions over time to distinguish temporary outages from permanent source death.
**Why:** A single failed update shouldn't mark a source DEAD permanently.
**Where:** New column or table for health history timestamps.
**Effort:** ~6-8 hours

#### 3.2 Cross-Source Discovery
**What:** Implement the automated source discovery protocol from BRAINSTORM.md Part 2.
**Why:** Enables proactive source failover instead of manual migration.
**Where:** New discovery service, rate limiting, confidence scoring.
**Effort:** ~20-30 hours (major feature)

#### 3.3 Multi-Source Chapter Resolution
**What:** Implement HIERARCHY/ROUND_ROBIN/QUALITY strategies from BRAINSTORM.md Part 3.
**Why:** Enables automatic chapter fetching from multiple sources per manga.
**Where:** New `source_mappings` table (Migration 15.sqm), resolution engine.
**Effort:** ~30-40 hours (major feature)

---

## Decision Points

1. **Should we add `source_mappings` table now or later?**
   - Current approach (Approach C) is lean: 3 columns, no new tables.
   - BRAINSTORM.md Approach A adds `source_mappings` table for full multi-source.
   - **Recommendation:** Stay with Approach C until health UI and bulk migration are shipped. Approach C → A is additive (no breaking changes).

2. **Should alt titles use JSON instead of pipe-delimited?**
   - Current: Pipe-separated in `TEXT` column. Simple but fragile.
   - Alternative: JSON array. More robust but slightly more complex queries.
   - **Recommendation:** Add to Phase 2 backlog. Low priority since `|` in titles is rare.

3. **Should we add a `Padding.micro` (2.dp) token?**
   - Some components use `1.dp` or `3.dp` padding that doesn't fit existing tokens.
   - **Recommendation:** Keep component-specific for truly unique values; add `micro` only if 3+ components need it.

---

## Architecture Decisions (Already Made)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Identity system | Approach C (Curated Search) | Minimal schema, zero API cost increase, upgradable to Approach A |
| Canonical ID format | `prefix:remoteId` (e.g., `al:21`) | Human-readable, supports multiple tracker backends |
| Health detection | Chapter count comparison (70% threshold) | Zero additional API calls, uses data already fetched |
| Search strategy | 4-tier with cross-title evaluation | Balances API cost vs match quality |
| Design tokens | Material Expressive system | Padding, Shape, Motion, Typography, Color tokens |
| Alt title source | AniList (romaji/english/native/synonyms) | Most complete metadata; MangaDex alt titles planned but SManga lacks field |
