# Ephyra Migration Plan

This document outlines the phased approach to fully modernize the Ephyra codebase.

> **Current Status (as of modernization sprint `copilot/refactor-codebase-architecture`):**
> Phases 1–3 are **complete**. Phase 4 domain-purity CI gate is in place. Phase 5 (UDF) is
> **complete** — all 20 ScreenModels and `ReaderViewModel` have a single `onEvent()` entry-point;
> no Android framework types (`Context`, `Activity`, `View`) appear in any event sealed class;
> one-shot UI effects flow via `Channel<Effect>` (`WebViewEffect`, `MangaCoverEffect`,
> `TrackInfoDialog.Model.Effect`).
> Phase 4 repository-to-interactor decomposition and Phase 6 Room migration are the active next
> steps. Build is green. Koin is at **4.2.1**. Legacy extension API compatibility is preserved.
> Boot-safety hardening pass completed: `fallbackToDestructiveMigration` added to Room,
> typed `MangaNotFoundException` replaces generic exceptions, `scope!!` eliminated.

## Phase 1: Foundation and Discovery ✅

- [x] Establish initial architecture documentation (`doc/ARCHITECTURE.md`).
- [x] Establish migration roadmap (`doc/MIGRATION_PLAN.md`).
- [x] Define validation criteria (`doc/VALIDATION_CRITERIA.md`).
- [x] Configure `ROADMAP.md` as the high-level index for documentation.

## Phase 2: Dependency Injection (Injekt to Koin) ✅

Migrate the legacy `Injekt` Service Locator to `Koin` Constructor Injection.

- [x] Identify all internal usages of `Injekt.get()`.
- [x] Replace global getters with constructor injection in `ScreenModel`s.
- [x] Ensure background workers and services are injected via Koin appropriately.
- [x] Remove `KoinJavaComponent.get()` and inline `KoinComponent` objects where they act as
  band-aids.
- [x] Remove internal `Injekt` dependencies (note: the `uy.kohesive.injekt.Injekt` shim must be kept
  for legacy extension compatibility).
- [x] Upgrade Koin to **4.2.1** (koin-core, koin-android, koin-androidx-compose,
  koin-androidx-workmanager, koin-annotations).
- [x] Configure `koinCompiler { compileSafety.set(false) }` on modules that inject types from manual
  `module {}` blocks. Note: `@ExternalDefinitions` is not available in Koin Annotations 4.2.x; this
  will be revisited when the annotation is available in a future Koin release.
- [x] Establish module-boundary architecture — feature modules depend only on domain interfaces, not
  concrete `:app` implementations.

## Phase 3: Synchronous Persistence to DataStore ✅

Replace blocking `SharedPreferences` operations with Flow-based `DataStore`.

- [x] Identify all synchronous read/write operations on the Main thread using legacy
  `AndroidPreferenceStore`.
- [x] Ensure preference classes implement `DataStorePreferenceStore`.
- [x] Refactor UI and business logic to collect preference flows rather than synchronous fetching.
- [x] Ensure `Dispatchers.IO` is strictly used for all disk I/O.
- [x] Replace remaining `.get()` suspend calls in non-coroutine contexts with `.getSync()` or
  `runBlocking{}` wrappers where needed for interface compatibility.

## Phase 4: Business Logic Isolation (The Interactor Mandate)

Move data access and business logic out of `ScreenModel`s into single-purpose Interactors.
Also enforce the domain purity boundary — `core/domain` must not import `ephyra.data.*`.

- [x] Audit massive "God Object" Repositories and `ScreenModel`s.
- [x] Extract `ExcludedScanlatorRepository` interface to `:domain`; implement in `:data`.
- [x] Move `toDbTrack()` / `toDomainTrack()` mappers from `core/domain` → `core/data`.
- [x] Remove dead `toDbChapter()` from `core/domain` (duplicate existed in `:data` mappers).
- [x] Move `ChapterSanitizer` from `data/src` → `domain/src/chapter/service/`.
- [x] Add CI fitness gate: zero `ephyra.data.*` imports in `core/domain` (except `DomainModule.kt`).
- [ ] Audit remaining `ScreenModel`s that inject a `Repository` directly instead of an `Interactor`.
- [ ] Break down remaining `MangaRepository` / `HistoryRepository` direct call-sites into focused
  Interactors.
- [ ] Port `backup/restore` handlers off `DatabaseHandler` → Interactors backed by Room DAOs.
- [ ] Lower the `feature → ephyra.data.*` ratchet baseline from 7 → 0.
  See **Known Violations** section below for the full tracked list.

### Known Violations — feature/presentation → data layer (tracked, not yet fixed)

These are confirmed architectural violations where feature or presentation-core modules import
directly from `ephyra.data.*` instead of going through a domain interface or presentation-core
re-export. Each item must be addressed before Phase 4 can be considered fully complete.

#### `feature/manga` + `feature/reader` (6 violations — same root cause) ⚠️ DEFERRED
| File | Violation | Fix | Blocker |
|------|-----------|-----|---------|
| `MangaCoverScreenModel.kt` | `import ephyra.data.saver.{Image,ImageSaver,Location}` | Extract `ImageSaver` interface + pure `Image`/`Location` to `core/domain`; keep Android impl in `:data` | `Location.directory(Context)` and `Image.Cover(Bitmap)` have Android deps — need careful API split |
| `ReaderViewModel.kt` | `import ephyra.data.saver.{Image,ImageSaver,Location}` | Same as above | Same |

#### `feature/reader` (1 violation) ⚠️ DEFERRED
| File | Violation | Fix | Blocker |
|------|-----------|-----|---------|
| `SaveImageNotifier.kt` | `import ephyra.data.notification.Notifications` | Move notification channel constants to `core/common` (Android-free constants, not data impl) | `Notifications` uses `NotificationManagerCompat` + `Context` for channel creation — only the `const val` IDs/channel strings should move; the `createChannels()` method must stay in data or app |

#### `feature/settings` (6 violations across 3 files) ⚠️ DEFERRED
| File | Violation | Fix | Blocker |
|------|-----------|-----|---------|
| `SettingsDataScreen.kt` | `import ephyra.data.export.{LibraryExporter,ExportOptions}` | Define `LibraryExporter` interface in `core/domain`; implement in `:data` | Large scope; backup/export system touches many data-layer types |
| `CreateBackupScreen.kt` | `import ephyra.data.backup.create.{BackupCreator,BackupOptions}` | Define `BackupCreator` interface + `BackupOptions` in `core/domain` | Same |
| `RestoreBackupScreen.kt` | `import ephyra.data.backup.{BackupFileValidator,restore.RestoreOptions}` | Define `BackupFileValidator` interface + `RestoreOptions` in `core/domain` | Same |
| `BackupSchemaScreen.kt` | `import ephyra.data.backup.models.Backup` | Move `Backup` model to `core/domain` (it is a pure data model, not an impl detail) | Backup model has many nested types that need moving |

### Known Violations — non-atomic state mutations ✅ FIXED

All `mutableState.value = mutableState.value.copy(…)` patterns replaced with
`mutableState.update { … }` in both affected files:

- ✅ `MatchResultsScreenModel` — 9 mutations fixed
- ✅ `AuthoritySearchScreenModel` — 21 mutations fixed

### Known Violations — `withUIContext` inside ViewModel ✅ FIXED

`withUIContext` (i.e. `withContext(Dispatchers.Main)`) inside a `ViewModel`/`ScreenModel` breaks
the UI Layer principle that ViewModels should be UI-thread-agnostic. State updates go through
`MutableStateFlow.update {}` (which is thread-safe); side-effects should be emitted as `Effect`
channel events and handled by the UI layer.

| File | Line | Fix applied |
|------|------|-------------|
| `ReaderViewModel.kt` | ~417 | ✅ `withUIContext` wrapper removed — `MutableStateFlow.update` is thread-safe on any dispatcher |
| `ReaderViewModel.kt` | ~1067 | ✅ `notifier.onComplete(uri)` moved to `ReaderActivity.onSaveImageResult(Success)`; ViewModel emits `Event.SavedImage(Success(uri))` only |

**Remaining note:** `SaveImageNotifier` is still constructed inside `ReaderViewModel.saveImage()`
using `app` (Application context) for the `onClear()` and `onError()` notification paths. Using
`Application` context for notification operations is acceptable (no Activity leak), but the
long-term clean fix is to emit `ReaderEffect.ClearSaveNotification` / `ReaderEffect.SaveError`
and move the `SaveImageNotifier` entirely to `ReaderActivity`.

## Phase 5: UI Architecture Stabilization (UDF) ✅

Ensure all screens adhere to Unidirectional Data Flow per the
[Android UI Layer guidelines](https://developer.android.com/topic/architecture/ui-layer):
every `ScreenModel`/`ViewModel` exposes exactly one `ViewState` and a single `onEvent(event)`
entry-point. Events must carry **no** Android framework types (`Context`, `Activity`, `View`).
One-shot UI side-effects (anything requiring Activity context) must flow **down** via a
`Channel<Effect>`, collected in the composable with `LaunchedEffect`.

- [x] `MangaScreenModel` — `MangaScreenEvent` sealed interface + `onEvent()` ✅
- [x] `HistoryScreenModel` — `HistoryScreenEvent` sealed interface + `onEvent()` ✅
- [x] `UpdatesScreenModel` — `UpdatesScreenEvent` sealed interface + `onEvent()` ✅
- [x] `LibraryScreenModel` — `LibraryScreenEvent` sealed interface + `onEvent()` ✅
- [x] `BrowseSourceScreenModel` — `BrowseSourceScreenEvent` + `onEvent()`; screens updated ✅
- [x] `MigrationListScreenModel` — `MigrationListScreenEvent` + `onEvent()`; `onMissingChapters`
  callback → `missingChaptersEvent` output channel ✅
- [x] **Batch A** (tiny — 1–2 public methods): `UpcomingScreenModel`, `UpdatesSettingsScreenModel`,
  `CoverSearchScreenModel`, `MigrateSourceScreenModel`, `MigrateMangaScreenModel`,
  `SourcesFilterScreenModel` ✅
- [x] **Batch B** (small — 3–6 methods): `SourcesScreenModel`, `WebViewScreenModel`,
  `ExtensionReposScreenModel`, `CategoryScreenModel`, `LibrarySettingsScreenModel`,
  `DownloadQueueScreenModel`, `ExtensionDetailsScreenModel`, `MangaCoverScreenModel` ✅
- [x] **Batch C** (medium): `SearchScreenModel` (abstract base — mutations `protected`),
  `AuthoritySearchScreenModel` ✅
- [x] **Batch D** (large — 26 methods): `ReaderViewModel` — `ReaderEvent` sealed interface (22
  events); all 26 public mutations privatized; `ReaderActivity` fully migrated to
  `viewModel.onEvent(…)` ✅
- [x] **Android UI Layer compliance** — `Context` removed from all event sealed classes:
  - `WebViewScreenEvent`: no `Context`; `WebViewEffect` channel (`ShareWebpage`,
    `OpenInBrowser`); `WebViewScreen` collects effects via `LaunchedEffect` ✅
  - `MangaCoverScreenEvent`: no `Context` on any of 5 events; `MangaCoverEffect.StartShare`
    channel; model uses injected `Application` throughout; `MangaScreen` collects effect ✅

**Remaining sub-items tracked in Phase 4 "Known Violations" section above:**
- `mutableState.value =` race conditions in `MatchResultsScreenModel` and
  `AuthoritySearchScreenModel` (replace with `mutableState.update {}`)
- `withUIContext` inside `ReaderViewModel` (lines ~417 and ~1067)

## Phase 6: Database Engine (SQLDelight to Room)

Replace the legacy SQL-first engine with Entity-DAO Room paradigm.

- [x] Design the Room `Entity` schema mimicking existing SQLDelight tables.
- [x] Implement Room `DAO`s with `Flow` and `Paging 3` integration.
- [x] Add `ExcludedScanlatorDao` and wire into `EphyraDatabase`.
- [x] Implement `MangaRepositoryImpl` backed by Room DAO.
- [x] Implement `ChapterRepositoryImpl` backed by Room DAO.
- [x] Implement `HistoryRepositoryImpl` backed by Room DAO.
- [ ] Implement `TrackRepositoryImpl` backed by Room DAO.
- [ ] Implement `CategoryRepositoryImpl` backed by Room DAO.
- [x] Boot-safety: `fallbackToDestructiveMigration(dropAllTables = true)` added while schema
  is actively evolving — prevents hard crash on Room identity-hash mismatch. This **must** be
  replaced with proper `addMigrations()` + `Migration` scripts before first production release.
- [ ] Implement robust versioned migration strategy (SQLite legacy schema → Room v1+).
- [ ] Add Room migration unit tests.
- [ ] Retire `AndroidDatabaseHandler` and remove SQLDelight dependency once all paths ported.

## Phase 7: Host-Extension API Verification ✅

Ensure modernizations do not break existing extensions.

- [x] Verify `eu.kanade.tachiyomi.source` package namespace is intact within the `source-api`
  module.
- [x] Verify network interceptors compatibility — `internal` visibility changes are scoped to the
  `:app` module boundary and do not affect the public extension API surface.
- [x] Validate Proguard rules protect `okhttp3`, `jsoup`, and other shared extension APIs from
  stripping or obfuscation.

## Phase 8: Boot-Safety & Runtime Reliability Hardening

Ensure successful builds translate to failure-free runtime behavior.

- [x] **Room schema crash prevention** — `fallbackToDestructiveMigration(dropAllTables = true)` in
  `AppModule` prevents `IllegalStateException` on Room identity-hash mismatch.  Will be replaced
  by versioned migrations before production.
- [x] **Typed `MangaNotFoundException`** — `domain/manga/model/MangaNotFoundException.kt` replaces
  generic `Exception("Manga not found")` in `MangaRepositoryImpl`; `GetManga.await()` catches it
  specifically; `GetManga.subscribe(id)` re-logs unexpected errors then re-throws so callers can
  distinguish "manga deleted" from I/O errors.
- [x] **`TachiyomiTextInputEditText.scope!!` eliminated** — local val replaces force-unwrap.

### Remaining items before PR merge is safe

These items represent the outstanding risks / violations that should be resolved (or
formally accepted and documented) before this PR is considered merge-ready:

#### High priority (potential silent failures or unexpected UX) — ✅ ALL COMPLETE
- [x] **`GlobalContext.get()` in `NotificationReceiver`** — migrated to `KoinComponent` +
  `by inject()` fields; two local lazy vars in `markAsRead` promoted to class-level injected
  fields.
- [x] **`GlobalContext.get()` in `ReaderPageImageView`** — removed; `alwaysDecodeLongStripWithSSIV`
  is now a constructor parameter (default `false`).  `WebtoonAdapter` reads the preference from
  `viewer.activity.getKoin()` and passes it at view construction time.
- [x] **`GlobalContext.get()` in widget (`BaseUpdatesGridGlanceWidget`)** — migrated to
  `KoinComponent` + `by inject()` fields; class now implements `KoinComponent`.

#### Medium priority (architecture violations, not immediate crash risks) — ✅ ALL COMPLETE
- [x] **`feature/settings` → `ephyra.data.*` violations** — **already fixed** before this
  session; `grep` finds no `ephyra.data.*` imports in `feature/settings`.
- [x] **`feature/manga` + `feature/reader` direct data imports** — **already fixed** before
  this session; `grep` finds no violations.
- [x] **`TrackRepositoryImpl` and `CategoryRepositoryImpl` not yet ported to Room** —
  **already complete**: both impls depend only on `TrackDao` / `CategoryDao` (Room).
- [ ] **Room versioned migrations** — replace `fallbackToDestructiveMigration` with real
  `Migration` objects before any production data is at risk.  Low urgency during active
  schema development; must be addressed before v1 release.

#### Lower priority (code quality / design hygiene)
- [x] **`withUIContext` in reader viewer classes** (`WebtoonPageHolder`, `PagerPageHolder`) —
  replaced with `withContext(Dispatchers.Main)`.  Both files now import
  `kotlinx.coroutines.Dispatchers` directly and no longer depend on the `withUIContext` util.
- [x] **`withUIContext` in settings composables** (`SettingsAdvancedScreen`,
  `SettingsDataScreen`, `SettingsTrackingScreen`) — all 13 call sites replaced with
  `withContext(Dispatchers.Main)`.  `withUIContext` imports removed; `Dispatchers` /
  `withContext` imports added where missing.
- [x] **`KoinJavaComponent.get()` in `BaseActivity`** — accepted pattern, documented.
- [ ] **`AndroidDatabaseHandler` / SQLDelight retirement** — four backup
  restorer/creator classes (`MangaBackupCreator`, `MangaRestorer`, `CategoriesRestorer`,
  `ExtensionRepoRestorer`) still call `handler.awaitList { categoriesQueries.* }`,
  `chaptersQueries.*`, etc. directly via the SQLDelight handler.  These need Room-based
  domain interactors for the missing query paths (e.g. `GetChaptersByMangaId` with
  `applyScanlatorFilter`, excluded-scanlators, `GetMangaSourceAndUrl`, backup track mapper)
  before the handler can be removed.  Blocking dependency for full SQLDelight retirement.

---

## Phase 9: Boot-Safety Round 2 — GlobalContext Audit

Audit and eliminate all remaining `GlobalContext.get()` call-sites in app/non-extension source.

### Resolved in this phase

- [x] **`presentation-core/TachiyomiTextInputEditText`** — `scope!!` force-unwrap eliminated
  (local val `newScope` mirrors the `app/` pattern).  `GlobalContext.get().get<BasePreferences>()`
  replaced with `KoinComponent + by inject()`.  The now-redundant single-argument
  `setIncognito(viewScope)` companion overload that hid a `GlobalContext` call was removed;
  the only remaining overload is the explicit `setIncognito(viewScope, preferences)` form.
- [x] **`app/TachiyomiTextInputEditText`** — same pattern; `GlobalContext.get().get()` replaced
  with `KoinComponent + by inject()`.
- [x] **`SourcePreferencesFragment`** — call-site of the now-removed single-arg `setIncognito`
  updated to the 2-arg form; `BasePreferences` injected into the fragment via `by inject()`.
- [x] **`DownloadCache.UniFileAsStringSerializer`** — the `object` serializer that called
  `GlobalContext.get().get<Application>()` in its `deserialize` path has been replaced with a
  proper design: `DownloadCache`'s constructor now takes `Application` (was `Context`; Koin
  already passed `androidApplication()`), a `private val proto: ProtoBuf` field is
  constructed with a `SerializersModule` that registers `UniFileSerializer(context)`, and the
  three `@Serializable(with = UniFileAsStringSerializer::class)` field annotations are replaced
  with `@Contextual`.  `GlobalContext` import removed from `DownloadCache.kt`.

### Accepted / out-of-scope GlobalContext usages

The following `GlobalContext` usages remain and are intentionally out of scope for this PR:

| File | Reason accepted |
|---|---|
| `source-api/HttpSource.kt` | Extension-facing public API; changing it would break all existing extensions. Extension API stability is a hard constraint. |
| `source-api/ConfigurableSource.kt` | Same as above. |
| `source-local/LocalSource.kt` | Tightly coupled to source-api extension contract; both `json` and `xml` are lazy and only accessed after Koin starts. |
| `Injekt.kt` | Legacy shim for extensions that still use the Injekt service locator. Delegates to Koin's `GlobalContext`. Safe: only called from extension code after startup. |
| `MigrationContext.kt` | Uses `GlobalContext.getOrNull()` — explicitly tolerates "Koin not started" by returning null. This is the correct pattern for migration code. |

### Remaining merge-blocking items

1. **Room versioned migrations** — replace `fallbackToDestructiveMigration` with explicit
   `Migration` objects before any production schema change.
2. **SQLDelight backup retirement** — backup restorer/creator classes still depend on
   `DatabaseHandler`.  Requires domain interactor coverage first.

---

## Phase 10: Compile-Time Safety (Koin → Hilt + Room/SQLDelight consolidation)

Replace all runtime-validated dependency injection and database infrastructure with
compile-time verified equivalents.  The root cause of post-build runtime crashes is that
Koin 4.2.1 is configured with `compileSafety.set(false)` — missing or misconfigured
bindings only surface as `NoBeanDefFoundException` crashes at runtime.

**Full details, task checklist, and session tracking are in
[`doc/COMPILE_SAFETY_PLAN.md`](COMPILE_SAFETY_PLAN.md).**

Summary of sub-phases:
- [ ] **Phase A** — Hilt bootstrap (`@HiltAndroidApp`, dual-boot period)
- [ ] **Phase B** — Core / data / domain Hilt modules
- [ ] **Phase C** — Network, preferences, app services Hilt modules
- [ ] **Phase D** — Workers (`@HiltWorker` / `HiltWorkerFactory`)
- [ ] **Phase E** — Feature module ScreenModels + Voyager-Hilt
- [ ] **Phase F** — Koin removal, `Injekt` shim re-backed to static registry
- [ ] **Phase G** — Room schema export, SQLDelight retirement, versioned migrations
- [ ] **Phase H** — CI enforcement gates (no-Koin, schema-export, no-fallback checks)
