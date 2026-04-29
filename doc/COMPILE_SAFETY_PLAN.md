# Compile-Time Safety Migration Plan

> **Objective**: Replace every runtime-validated dependency with a compile-time validated
> equivalent so that _no_ DI-graph failure, missing-binding crash, or SQL-type mismatch can
> survive a successful build.  A green build must equal a running app.
>
> **Root cause**: Koin 4.2.1 is configured with `compileSafety.set(false)` across all modules
> because the module graph is expressed as runtime DSL (`module { single { … } }`) rather than
> as annotated classes.  The compiler plugin cannot validate what it cannot see at compile time.
> Any missing or misconfigured binding produces a `NoBeanDefFoundException` at **runtime**, often
> on the first screen open.
>
> **Solution**: Migrate to **Hilt** (Dagger / Google), the Android-standard compile-time DI
> framework.  Hilt verifies the complete dependency graph during `kapt`/`ksp` processing; a
> build that compiles is a build whose DI graph is correct.

---

## Why Hilt over "just enable Koin annotations"

| Criterion | Koin annotations + `compileSafety.set(true)` | Hilt |
|---|---|---|
| True compile-time graph | Partial — only annotated modules; `@ExternalDefinitions` still experimental | Full — every binding verified by `dagger.hilt` annotation processor |
| Industry standard | No | Yes (Google, official Android docs) |
| Multi-module support | Manual `@ExternalDefinitions` per module | First-class `@InstallIn` scopes |
| WorkManager integration | Manual `WorkerFactory` wired in Koin | `@HiltWorker` + `HiltWorkerFactory` |
| ViewModel integration | `@KoinViewModel` (Voyager only) | `@HiltViewModel` (first-class Jetpack) |
| Test support | `startKoin{}` / `stopKoin{}` in every test | `@HiltAndroidTest` + `@TestInstallIn` |
| Maintenance burden | Internal DSL migration + shim maintenance | Documented, stable, long-lived |

Koin compile-safety with annotations would require the same volume of code rewrites as Hilt,
with fewer guarantees and ongoing `@ExternalDefinitions` workarounds per module.

---

## Scope

| Layer | Files affected | Koin pattern used |
|---|---|---|
| `app` — DI modules | 4 files | `module { single/factory/worker { } }` DSL |
| `app` — Application class | 1 file | `startKoin { }`, `by inject()` |
| `app` — Workers (8 classes) | 8 files | `worker { }` DSL |
| `app` — Activities / Screens | ~10 files | `by inject()`, `koinInject<>()` |
| `feature/*` — Screens | 63 files | `koinScreenModel { }`, `koinInject<>()` |
| `feature/*` — ScreenModels | ~27 files | constructor `get()` args wired via Koin |
| `core/*` / `data` / `domain` | ~30 files | `api(libs.koin.core)` dependency |
| `source-api` / `Injekt.kt` | 2 files | legacy shim → Koin `GlobalContext` |

**Total impacted files ≈ 145.**  Migration is split into six phases; each phase produces a
green build and passes all existing unit tests before the next phase begins.

---

## Technology Decisions

### DI: Koin → Hilt

```
com.google.dagger:hilt-android          (replaces koin-android)
com.google.dagger:hilt-compiler (ksp)   (replaces koin-compiler-plugin + koin-annotations)
androidx.hilt:hilt-work                 (replaces koin-androidx-workmanager)
androidx.hilt:hilt-compiler (ksp)       (WorkManager Hilt integration)
cafe.adriel.voyager:voyager-hilt        (replaces voyager-koin)
```

### Database: Room (already in progress, Phase 6)

Room with KSP **already is** compile-time validated — all `@Query` SQL is verified during the
annotation processing step; a bad query is a build error, not a runtime crash.  No database
engine change is required.  The remaining Phase 6 work (SQLDelight retirement, versioned
migrations) is tracked in this plan as **Phase G**.

### Extension compatibility: Injekt shim

The `uy.kohesive.injekt.Injekt` object is a public API surface used by legacy extensions.
It cannot be removed.  Currently it delegates to Koin's `GlobalContext`.  After Koin is
removed, it will delegate to a **thin static registry** populated during `Application.onCreate()`
by Hilt entry-points.  The extension-facing API is unchanged.

---

## Migration Phases

### Phase A — Hilt Bootstrap ⬜

Set up Hilt infrastructure without changing any feature code.

- [ ] **A-1** Add Hilt to `libs.versions.toml`: `hilt = "2.56"`, `hilt-work = "1.2.0"`.
- [ ] **A-2** Add library aliases: `hilt-android`, `hilt-compiler`, `hilt-androidx-work`,
  `hilt-androidx-work-compiler`, `voyager-hilt`.
- [ ] **A-3** Add plugin alias: `hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }`.
- [ ] **A-4** Update `buildSrc` convention plugins: add `hilt` and `ksp` as available plugins
  in `ephyra.android.application.gradle.kts` and `ephyra.library.gradle.kts`.
- [ ] **A-5** Apply `id("dagger.hilt.android.plugin")` and
  `ksp(libs.hilt.compiler)` in `app/build.gradle.kts`.
- [ ] **A-6** Annotate `App : Application` with `@HiltAndroidApp`; remove `startKoin { }` call
  from `App.onCreate()` (Koin modules are removed in Phase E, but `startKoin` is commented
  out here so the app still builds — it will be re-enabled temporarily with stubs).
- [ ] **A-7** Verify: `./gradlew :app:compileDebugKotlin` passes with both Hilt and Koin
  simultaneously present (dual-boot period).
- [ ] **A-8** Commit & push.

### Phase B — Core Module Injection ⬜

Migrate `core/common`, `core/data`, `core/domain`, `data`, `domain`.

- [ ] **B-1** Replace `api(libs.koin.core)` in `core/common/build.gradle.kts` with
  `api(libs.hilt.android)`.  Update `Injekt.kt` to use a static `ConcurrentHashMap` registry
  instead of Koin's `GlobalContext` (extension-facing API unchanged).
- [ ] **B-2** Create `app/src/main/java/ephyra/app/di/InjektEntryPoint.kt`:
  an `@EntryPoint @InstallIn(SingletonComponent::class)` that exposes the ~10 types extensions
  need (`NetworkHelper`, `SourceManager`, `Json`, etc.).  Populate the `Injekt` registry from
  this entry point in `App.onCreate()` after Hilt inject.
- [ ] **B-3** In `data/build.gradle.kts`, replace `koin-core` / `koin-annotations` with Hilt.
  Remove `ksp(libs.room.compiler)` duplication (it stays).
- [ ] **B-4** Create `data/src/main/java/ephyra/data/di/DataModule.kt`:
  `@Module @InstallIn(SingletonComponent::class)` providing `EphyraDatabase`, all DAOs,
  `Json`, `XML`, `ProtoBuf` singletons.  This replaces the `Room.databaseBuilder` and DAO
  bindings currently in `koinAppModule`.
- [ ] **B-5** Create `domain/src/main/java/ephyra/domain/di/DomainModule.kt`:
  `@Module @InstallIn(SingletonComponent::class)` replacing `koinDomainModule` — repository
  interface → implementation bindings.
- [ ] **B-6** Create `domain/src/main/java/ephyra/domain/di/InteractorModule.kt`:
  all domain interactors that are currently wired in `koinDomainModule`.
- [ ] **B-7** Annotate repository `Impl` classes with `@Inject constructor(…)` (no Koin).
- [ ] **B-8** Annotate all domain interactors with `@Inject constructor(…)`.
- [ ] **B-9** Run `./gradlew :data:kspDebugKotlin :domain:kspDebugKotlin` — verify zero errors.
- [ ] **B-10** Commit & push.

### Phase C — Network, Preferences, and App Services ⬜

Migrate `NetworkHelper`, `PreferenceStore`, all preference classes, and top-level app services.

- [ ] **C-1** Create `app/src/main/java/ephyra/app/di/NetworkModule.kt`:
  `@Module @InstallIn(SingletonComponent::class)` providing `NetworkHelper`, `JavaScriptEngine`,
  `OkHttpClient`, `ExtensionApi`.
- [ ] **C-2** Create `app/src/main/java/ephyra/app/di/PreferenceModule.kt` (Hilt version):
  `@Module @InstallIn(SingletonComponent::class)` providing `PreferenceStore`,
  `NetworkPreferences`, `SourcePreferences`, `SecurityPreferences`, `PrivacyPreferences`,
  `LibraryPreferences`, `UpdatesPreferences`, `ReaderPreferences`, `TrackPreferences`,
  `DownloadPreferences`, `BackupPreferences`, `StoragePreferences`, `UiPreferences`,
  `BasePreferences`.
- [ ] **C-3** Create `app/src/main/java/ephyra/app/di/StorageModule.kt`: `StorageManager`,
  `AndroidStorageFolderProvider`, `LocalSourceFileSystem`, `LocalCoverManager`.
- [ ] **C-4** Create `app/src/main/java/ephyra/app/di/DownloadModule.kt`: `DownloadStore`,
  `DownloadProvider`, `DownloadCache`, `DownloadManager`, `Downloader`,
  `DownloadPendingDeleter`.
- [ ] **C-5** Create `app/src/main/java/ephyra/app/di/TrackingModule.kt`: `TrackerManager`,
  `DelayedTrackingStore`, `TrackingJobScheduler`.
- [ ] **C-6** Create `app/src/main/java/ephyra/app/di/BackupModule.kt`: all backup
  creator/restorer singletons.
- [ ] **C-7** Create `app/src/main/java/ephyra/app/di/ExtensionModule.kt`: `ExtensionLoader`,
  `ExtensionInstaller`, `SourceManager`, `ExtensionManager`.
- [ ] **C-8** Create `app/src/main/java/ephyra/app/di/UiModule.kt`: `AppInfo`,
  `AppNavigator`/`NavigatorImpl`, `ThemingDelegate`, `SecureActivityDelegate`,
  `CrashLogUtil`, `NotificationManager`, screen factories.
- [ ] **C-9** Annotate all newly provided classes with `@Inject constructor(…)` where possible
  to eliminate manual `@Provides` functions.
- [ ] **C-10** Run `./gradlew :app:kspDebugKotlin` — verify zero Hilt errors.
- [ ] **C-11** Commit & push.

### Phase D — Workers ⬜

Replace Koin `worker { }` DSL with `@HiltWorker` / `HiltWorkerFactory`.

- [ ] **D-1** Add `implementation(libs.hilt.androidx.work)` and
  `ksp(libs.hilt.androidx.work.compiler)` to `app/build.gradle.kts`.
- [ ] **D-2** For each of the 8 worker classes:
  `AppUpdateDownloadJob`, `BackupCreateJob`, `BackupRestoreJob`, `DownloadJob`,
  `DelayedTrackingUpdateJob`, `MatchUnlinkedJob`, `MetadataUpdateJob`, `LibraryUpdateJob`:
  - Replace `(appContext: Context, params: WorkerParameters)` constructor with
    `@HiltWorker @AssistedInject constructor(@Assisted appContext: Context, @Assisted params: WorkerParameters, …injected deps…)`.
  - Remove from Koin `worker { }` registrations.
- [ ] **D-3** In `App.workManagerConfiguration`, replace `get<WorkerFactory>()` (Koin) with
  `HiltWorkerFactory` obtained via `@Inject`.
- [ ] **D-4** Verify `./gradlew :app:kspDebugKotlin` passes.
- [ ] **D-5** Commit & push.

### Phase E — Feature Modules + Voyager-Hilt ⬜

Migrate all 63 Voyager screens and 27 ScreenModels.

- [ ] **E-1** In each feature `build.gradle.kts`, replace `alias(libs.plugins.koin.compiler)`,
  `api(libs.koin.core)`, `implementation(libs.koin.annotations)`,
  `implementation(libs.koin.androidx.compose)` with `implementation(libs.hilt.android)` and
  `ksp(libs.hilt.compiler)`.
- [ ] **E-2** Replace `implementation(libs.voyager.koin)` with
  `implementation(libs.voyager.hilt)` in every feature `build.gradle.kts`.
- [ ] **E-3** For each ScreenModel class annotated with Koin (`@KoinViewModel` or wired in a
  `factory { }` block):
  - Add `@Inject constructor(…)` — dependencies are now provided by Hilt.
  - ScreenModels that receive navigation parameters (e.g. `mangaId: Long`) use
    `@AssistedInject` + `@AssistedFactory`.
  - Remove corresponding `factory { }` blocks from `KoinModules.kt`.
- [ ] **E-4** In each Voyager `Screen.Content()`, replace:
  - `koinScreenModel<MyScreenModel>()` → `getScreenModel<MyScreenModel>()` (voyager-hilt)
  - `koinInject<Dep>()` → `hiltInject<Dep>()` (or constructor-injected into the ScreenModel)
  - `rememberKoinInject<Dep>()` → `rememberHiltInject<Dep>()`
- [ ] **E-5** Annotate all activities that use injection (`ReaderActivity`,
  `WebViewActivity`, `BaseOAuthLoginActivity`) with `@AndroidEntryPoint`.
- [ ] **E-6** Replace `by inject()` in all activity/fragment fields with constructor injection
  or `@Inject lateinit var` (Hilt field injection for Android entry points).
- [ ] **E-7** Batch verification: `./gradlew :feature:browse:kspDebugKotlin` (etc.) — all
  feature modules compile green.
- [ ] **E-8** Commit & push.

### Phase F — Koin Removal + Injekt Shim Update ⬜

Delete all Koin infrastructure once every callsite is migrated.

- [ ] **F-1** Delete `app/src/main/java/ephyra/app/di/AppModule.kt` (Koin DSL version).
- [ ] **F-2** Delete `app/src/main/java/ephyra/app/di/KoinModules.kt` (koinAppModule_UI).
- [ ] **F-3** Delete old `app/src/main/java/ephyra/app/di/PreferenceModule.kt` (Koin version).
- [ ] **F-4** Delete `core/domain/src/main/java/ephyra/domain/DomainModule.kt` (Koin version).
- [ ] **F-5** Remove `startKoin { }` block from `App.onCreate()`.
- [ ] **F-6** Update `Injekt.kt` (extension shim):
  - Remove `import org.koin.*` imports.
  - Replace `GlobalContext.get().get(…)` with a lookup into a static
    `ConcurrentHashMap<KClass<*>, () -> Any>` that is populated by the Hilt entry-point in
    `App.onCreate()`.
  - Extension-facing `Injekt.get<T>()` API is **unchanged**.
- [ ] **F-7** Remove all Koin library declarations from `libs.versions.toml`:
  `koin`, `koin_annotations`, `koin_compiler_plugin`, and all `koin-*` library aliases.
- [ ] **F-8** Remove Koin from every `build.gradle.kts` (all `koin.*` lines, `koinCompiler { }`
  blocks, `bundles.koin`, `libs.voyager.koin`).
- [ ] **F-9** Delete `app/src/test/java/ephyra/app/di/KoinModuleInterfaceBindingTest.kt` and
  `AppModuleImplementationContractTest.kt` — replace with equivalent Hilt component tests using
  `@HiltAndroidTest` + `HiltTestApplication`.
- [ ] **F-10** Full build: `./gradlew assembleDebug` — must be green with zero Koin references.
- [ ] **F-11** Run all unit tests: `./gradlew testDebugUnitTest`.
- [ ] **F-12** Commit & push.

### Phase G — Room Finalization + SQLDelight Retirement ⬜

Complete Phase 6 from `MIGRATION_PLAN.md`.

- [ ] **G-1** Enable Room schema export: set `exportSchema = true` in `EphyraDatabase`,
  add `room.schemaLocation` to `javaCompileOptions` in `data/build.gradle.kts`, commit the
  initial schema JSON to `data/schemas/`.
- [ ] **G-2** Port remaining backup creator/restorer classes off `AndroidDatabaseHandler`:
  - `MangaBackupCreator` — replace `handler.awaitList { chaptersQueries.* }` with Room
    `ChapterDao` / `MangaDao` calls.
  - `MangaRestorer` — replace SQLDelight-backed `GetChaptersByMangaId` with Room DAO.
  - `CategoriesRestorer` / `ExtensionRepoRestorer` — replace `categoriesQueries.*` /
    `extensionReposQueries.*` with Room DAOs.
- [ ] **G-3** Remove `AndroidDatabaseHandler`, `DatabaseHandler`, `DatabaseAdapter`,
  `QueryPagingSource`, `TransactionContext` (SQLDelight infrastructure).
- [ ] **G-4** Remove SQLDelight plugin and all `sqldelight-*` dependencies from
  `libs.versions.toml` and `data/build.gradle.kts`.
- [ ] **G-5** Delete `data/src/main/sqldelight/` directory tree.
- [ ] **G-6** Replace `fallbackToDestructiveMigration(dropAllTables = true)` in `DataModule.kt`
  with explicit `Migration(1, 2)` objects.  Write a unit test for each migration using
  `MigrationTestHelper`.
- [ ] **G-7** Run `./gradlew :data:testDebugUnitTest` — all Room migration tests pass.
- [ ] **G-8** Commit & push.

### Phase H — CI Enforcement Gates ⬜

Make compile-time safety permanent and machine-enforced.

- [ ] **H-1** Add a CI step `"No Koin references"` that greps all `*.kt` / `*.gradle.kts`
  source for `org.koin`, `io.insert-koin`, `koin-`, `koinInject`, `koinScreenModel`,
  `startKoin`, and fails the build if any are found (excluding `Injekt.kt` shim).
- [ ] **H-2** Add a CI step `"Room schema exported"` that fails if
  `data/schemas/ephyra.data.room.EphyraDatabase/` does not contain a JSON file for the
  current database version.
- [ ] **H-3** Add a CI step `"No fallbackToDestructiveMigration"` that greps all
  `*.kt` source for `fallbackToDestructiveMigration` and fails if found.
- [ ] **H-4** Add a CI step `"No GlobalContext"` that fails if `GlobalContext.get()` appears
  in any file except `Injekt.kt` (the accepted extension shim).
- [ ] **H-5** Confirm all existing CI gates still pass (architecture fitness, domain purity,
  `Injekt.get()` gate).
- [ ] **H-6** Commit & push.

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Voyager-Hilt missing `@AssistedInject` for parameterised ScreenModels | Medium | High | Use `@AssistedFactory` pattern; Voyager-Hilt documents this |
| `source-api` extensions break when Injekt shim changes | Low | High | Shim API is unchanged; only the backing store changes |
| Room `fallbackToDestructiveMigration` destroys user data | Low (dev/test only) | High | Remove before production; add migration tests in G-6 |
| Hilt build times increase (kapt/ksp overhead) | Medium | Medium | Already using KSP for Room; Hilt KSP is comparable |
| Circular dependencies surfaced by Hilt's strict graph checking | Low | Medium | Fix violations as they are discovered — this is the goal |

---

## Completion Criteria

A build is considered "compile-time safe" when all of the following are true:

1. `./gradlew assembleDebug` is green with **zero** Koin imports anywhere in `:app`, `:core:*`,
   `:data`, `:domain`, `:feature:*`, `:presentation-core`, `:presentation-widget`.
2. The Hilt component graph is fully resolved at compile time — no `@Provides` method with a
   runtime-only `get()` call.
3. All Room `@Query` annotations compile without `@SuppressWarnings` on any SQL error.
4. Room schema is exported and versioned; `fallbackToDestructiveMigration` is absent.
5. All CI gates in Phase H pass on every PR.
6. `./gradlew testDebugUnitTest` passes for all modules.

---

## Session Tracking

| Session | Phase(s) | Status |
|---|---|---|
| 1 (current) | Plan creation | ✅ Plan written |
| 2 | A (Hilt bootstrap) | ⬜ |
| 3 | B (Core / data / domain modules) | ⬜ |
| 4 | C (Network / preferences / services) | ⬜ |
| 5 | D (Workers) | ⬜ |
| 6 | E-1 through E-4 (Feature modules batch 1) | ⬜ |
| 7 | E-5 through E-8 (Feature modules batch 2) | ⬜ |
| 8 | F (Koin removal) | ⬜ |
| 9 | G (SQLDelight retirement) | ⬜ |
| 10 | H (CI enforcement) | ⬜ |
