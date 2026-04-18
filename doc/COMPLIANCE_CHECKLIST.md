# Ephyra Design-Principles Compliance Checklist

This document is the canonical audit of the codebase against
[`DESIGN_PRINCIPLES.md`](./DESIGN_PRINCIPLES.md).  Every row is a concrete,
actionable work item.  Items are marked **✅ Fixed** once the change has been
merged to the working branch, **🔧 In Progress**, or **⏳ Pending**.

> **Rule:** No item may be closed without a merge commit that resolves the
> violation.  Partial fixes must stay open.

---

## Principle 1 — The Dependency Rule Is Absolute (no `android.*` in domain)

### Tier 1 — Completed in this session

| Status | File | Violation | Fix Applied |
|--------|------|-----------|-------------|
| ✅ | `domain/reader/model/ReaderOrientation.kt` | `ActivityInfo.*` constants imported for orientation ints | Replaced with raw int values; `import android.content.pm.ActivityInfo` removed |
| ✅ | `domain/backup/service/BackupScheduler.kt` | `android.net.Uri` in interface signature | Parameter type changed to `String` |
| ✅ | `domain/backup/service/BackupNotifier.kt` | `android.net.Uri` in interface signature | Parameter type changed to `String` |
| ✅ | `domain/backup/service/RestoreScheduler.kt` | `android.net.Uri` in interface signature | Parameter type changed to `String` |
| ✅ | `domain/library/service/LibraryUpdateNotifier.kt` | `android.net.Uri` in interface signature | Parameter type changed to `String` |
| ✅ | `domain/release/service/AppUpdateNotifier.kt` | `android.net.Uri` in interface signature | Parameter type changed to `String` |
| ✅ | `domain/library/service/LibraryUpdateScheduler.kt` | `android.content.Context` in `startNow()` interface | Parameter removed; implementations use constructor-injected context |
| ✅ | `domain/download/service/DownloadNotifier.kt` | `android.app.PendingIntent` in `onWarning()` interface | Parameter removed; implementation creates its own intent from `mangaId` |

### Tier 2 — Structural work (requires moving classes out of domain)

These files import Android framework types because they ARE Android classes
(Worker, SharedPreferences, geometry).  The correct fix is to relocate them to
`:data` or `:app`, or to introduce a pure-Kotlin domain abstraction backed by a
platform implementation.

| Status | File | Violation | Required Fix |
|--------|------|-----------|--------------|
| ⏳ | `core/domain/.../track/store/DelayedTrackingStore.kt` | `android.content.Context` + `SharedPreferences` | Move to `:data` module; inject via `TrackingQueueStore` interface in domain |
| ⏳ | `core/domain/.../track/service/DelayedTrackingUpdateJob.kt` | `CoroutineWorker`, `Context`, `WorkManager` | Move to `:app` module; register with existing `WorkerFactory` |
| ⏳ | `core/domain/.../track/interactor/TrackChapter.kt` | `android.content.Context` (passed to `DelayedTrackingUpdateJob.setupTask`) | Remove context from interactor; have the job scheduler interface (in domain) accept the parameters |
| ⏳ | `core/domain/.../base/ExtensionInstallerPreference.kt` | `android.content.Context` (MIUI / Shizuku checks) | Extract `InstallerCapabilityProvider` interface in domain; implement in `:app` |
| ⏳ | `core/domain/.../base/BasePreferences.kt` | `android.content.Context` property stored on class | Remove context from `BasePreferences`; `ExtensionInstallerPreference` receives its capabilities via interface |
| ✅ | `core/domain/.../extension/interactor/TrustExtension.kt` | `android.content.pm.PackageInfo` in `isTrusted()` | Introduced `data class ExtensionPackageInfo(packageName, versionCode)` in `domain/extension/model`; `TrustExtension.isTrusted()` now accepts it; `ExtensionLoader.kt` converts from `PackageInfo` at the `:app` boundary |
| ⏳ | `domain/storage/service/StorageManager.kt` | `android.content.Context` + `UniFile` (Android) | Move to `:data`; expose `StorageDirectoryProvider` interface in domain |
| ⏳ | `domain/reader/util/RectFExtensions.kt` | `android.graphics.RectF` | Introduce `data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float)` in domain; keep `RectF` in `:presentation-core` |

---

## Principle 2 — Module Boundaries Are Interface Boundaries

Feature modules must import only domain interfaces.  These files bypass the
boundary and import concrete `:data` implementations directly.

| Status | File | Offending Import(s) | Required Fix |
|--------|------|---------------------|--------------|
| ⏳ | `feature/browse/.../SourcePreferencesScreen.kt` | `ephyra.data.preference.SharedPreferencesDataStore` | Inject `PreferenceStore` domain interface |
| ⏳ | `feature/browse/.../BrowseSourceScreenModel.kt` | `ephyra.data.cache.CoverCache` | Inject `ephyra.domain.manga.service.CoverCache` interface |
| ⏳ | `feature/library/.../LibraryScreenModel.kt` | `ephyra.data.cache.CoverCache` | Inject domain interface |
| ⏳ | `feature/manga/.../MangaCoverScreenModel.kt` | `CoverCache`, `ImageSaver`, `Image`, `Location` (all `:data`) | Formalise `ImageSaver` interface in domain; inject domain types |
| ⏳ | `feature/manga/.../MangaScreen.kt` | `ephyra.data.cache.CoverCache` | Inject domain interface |
| ✅ | `feature/manga/.../track/TrackInfoDialog.kt` | `ephyra.data.track.DeletableTracker` | Moved `DeletableTracker` marker interface to `ephyra.domain.track.service`; old `:data` file deleted |
| ⏳ | `feature/migration/.../MigrateMangaDialog.kt` | `ephyra.data.cache.CoverCache` | Inject domain interface |
| ⏳ | `feature/reader/.../ReaderViewModel.kt` | `ChapterCache`, `CoverCache`, `toDomainChapter`, `ImageSaver`, `Image`, `Location` | Formalise interfaces in domain; use mapper in `:data` only |
| ⏳ | `feature/reader/.../SaveImageNotifier.kt` | `ephyra.data.notification.Notifications` | Extract notification-channel constants to `:core:common` or `:presentation-core` |
| ⏳ | `feature/reader/.../loader/ChapterLoader.kt` | `ephyra.data.cache.ChapterCache` | Extract `ChapterCache` interface to domain |
| ⏳ | `feature/reader/.../loader/HttpPageLoader.kt` | `ChapterCache`, `toDomainChapter` | Same as above; mapper in `:data` |
| ⏳ | `feature/reader/.../model/ReaderChapter.kt` | `ephyra.data.database.models.Chapter` | Store domain `Chapter` model, not database entity |
| ⏳ | `feature/reader/.../viewer/ReaderPageImageView.kt` | `ephyra.data.coil.cropBorders`, `customDecoder` | Move Coil utilities to `:presentation-core` or `:core:data` — accept via lambda/interface |
| ⏳ | `feature/reader/.../viewer/MissingChapters.kt` | `ephyra.data.database.models.toDomainChapter` | Use domain `Chapter` at feature boundary |
| ⏳ | `feature/settings/.../SettingsDataScreen.kt` | `ChapterCache`, `LibraryExporter`, `ExportOptions` | Extract `LibraryExporter` interface to domain |
| ⏳ | `feature/settings/.../SettingsDataScreenModel.kt` | `ephyra.data.cache.ChapterCache` | Extract `ChapterCache` interface to domain |
| ⏳ | `feature/settings/.../SettingsTrackingScreen.kt` | `AnilistApi`, `BangumiApi`, `MyAnimeListApi`, `ShikimoriApi` | Expose required capabilities via `Tracker` interface; no feature module needs concrete API |
| ✅ | `feature/settings/.../about/AboutScreen.kt` | `ephyra.data.updater.RELEASE_URL` | Added `releaseUrl` computed property to `AppInfo` interface (presentation-core); `AboutScreen` now uses `appInfo.releaseUrl` |
| ✅ | `feature/settings/.../about/AboutScreenModel.kt` | `ephyra.data.updater.AppUpdateChecker` | Replaced with `GetApplicationRelease` interactor; `AppInfo.githubRepo` added to provide repo slug; `checkVersion()` no longer needs `Context` parameter |
| ⏳ | `feature/settings/.../data/CreateBackupScreen.kt` | `BackupCreator`, `BackupOptions` (`:data`) | Extract `BackupOptions` value type to domain |
| ⏳ | `feature/settings/.../data/RestoreBackupScreen.kt` | `BackupFileValidator`, `RestoreOptions` (`:data`) | Extract `RestoreOptions` and a validator interface to domain |
| ⏳ | `feature/settings/.../debug/BackupSchemaScreen.kt` | `ephyra.data.backup.models.Backup` | Move `Backup` model to domain `backup` package |
| ⏳ | `feature/presentation/reader/ChapterTransition.kt` | `ephyra.data.database.models.toDomainChapter` | Use domain `Chapter` at presentation boundary |

---

## Principle 3 — One Direction, One Source of Truth

`ScreenModel`s must inject only domain **Interactors**, never repositories
or preference stores directly.

| Status | File | Violation | Fix Applied / Required |
|--------|------|-----------|------------------------|
| ✅ | `feature/browse/.../authority/MatchResultsScreenModel.kt` | Constructor injects `MangaRepository` directly | Replaced with `GetFavorites` interactor |
| ✅ | `feature/browse/.../authority/AuthoritySearchScreenModel.kt` | Constructor injects `MangaRepository` for 4 distinct operations | Replaced with `GetFavoritesByCanonicalId`, `GetDuplicateLibraryManga` (new `invoke(title)` overload added), `GetMangaByUrlAndSourceId`, `UpdateManga`, `NetworkToLocalManga` |

---

## Principle 4 — Explicit Failure, Never Silent Failure

### Completed in this session

| Status | File | Violation | Fix Applied |
|--------|------|-----------|-------------|
| ✅ | `app/.../App.kt:332` | `catch (_: Exception) {}` in `getPackageName()` — no log | `logcat(WARN)` added |
| ✅ | `app/.../LibraryUpdateJob.kt:339` | `catch (_: Exception) {}` on metadata fetch — no log | `logcat(WARN)` with manga title added |
| ✅ | `core/data/.../MangaCoverFetcher.kt:270` | `catch (ignored: Exception) {}` on `editor.abort()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/data/.../ChapterCache.kt:125` | `catch (_: Exception) {}` on `editor.abort()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/data/.../ChapterCache.kt:191` | `catch (_: Exception) {}` on `editor.abort()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/data/.../ChapterCache.kt:229` | `catch (_: Exception) {}` on `editor.abort()` — no log | `logcat(DEBUG)` added |
| ✅ | `feature/reader/.../ReaderPagePreProcessor.kt:79` | `catch (_: Exception) { /* skip */ }` on dimension read — no log | `logcat(DEBUG)` added |
| ✅ | `core/download/.../Downloader.kt:685` | `catch (_: Exception) { /* skip */ }` on dimension read — no log | `logcat(DEBUG)` added |
| ✅ | `core/domain/.../MigrateMangaUseCase.kt:53` | `catch (_: Exception) { // Worst case... }` on chapter sync — no log | `logcat(WARN)` added |
| ✅ | `core/common/.../ContextExtensions.kt:48` | `catch (e: Exception) { // toast(e.message) }` on `startActivity` — no log | `logcat(ERROR)` added |
| ✅ | `feature/settings/.../SettingsTrackingScreen.kt:619` | `catch (_: Exception) {}` on Jellyfin library fetch — no log | `logcat(WARN)` added |
| ✅ | `app/.../migration/.../MigrationListScreenModel.kt:160` | `catch (_: Exception) {}` on thumbnail detail fetch — no log | `logcat(WARN)` added |
| ✅ | `app/.../migration/.../MigrationListScreenModel.kt:293` | `catch (_: Exception) {}` on batch migration detail — no log | `logcat(WARN)` added |

## Principle 4 — Explicit Failure, Never Silent Failure

### Completed in this session

| Status | File | Violation | Fix Applied |
|--------|------|-----------|-------------|
| ✅ | `app/.../App.kt:332` | `catch (_: Exception) {}` in `getPackageName()` — no log | `logcat(WARN)` added |
| ✅ | `app/.../LibraryUpdateJob.kt:339` | `catch (_: Exception) {}` on metadata fetch — no log | `logcat(WARN)` with manga title added |
| ✅ | `core/data/.../MangaCoverFetcher.kt:270` | `catch (ignored: Exception) {}` on `editor.abort()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/data/.../ChapterCache.kt:125` | `catch (_: Exception) {}` on `editor.abort()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/data/.../ChapterCache.kt:191` | `catch (_: Exception) {}` on `editor.abort()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/data/.../ChapterCache.kt:229` | `catch (_: Exception) {}` on `editor.abort()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/data/.../ChapterCache.kt:145` | `catch (_: IOException) {}` in `isImageInCache()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/data/.../track/jellyfin/JellyfinApi.kt:505` | `catch (_: Exception)` in `checkServerReachable()` — returns false silently | `logcat(DEBUG)` added |
| ✅ | `core/data/.../track/kitsu/Kitsu.kt:162` | `catch (_: Exception)` in `restoreToken()` — returns null silently | `logcat(DEBUG)` added |
| ✅ | `data/.../manga/MangaMapper.kt:337` | `catch (_: Exception)` on JSON parse fall-through in `parseAlternativeTitles()` — no log | `logcat(DEBUG)` added |
| ✅ | `feature/reader/.../viewer/pager/Pager.kt:75,87,89,91` | 4 defensive touch-event catches — crash-suppression with no diagnostic log | `logcat(DEBUG)` added to all four; comment preserved explaining the Android platform bug |
| ✅ | `feature/webview/.../WebViewScreenModel.kt:40` | `shareWebpage` catch shows toast but emits nothing to logcat | `logcat(WARN, e)` added alongside existing toast |
| ✅ | `feature/reader/.../ReaderPagePreProcessor.kt:79` | `catch (_: Exception) { /* skip */ }` on dimension read — no log | `logcat(DEBUG)` added |
| ✅ | `feature/reader/.../ReaderPagePreProcessor.kt:149` | `catch (_: Exception)` in `resolveBlockedDHashes()` — no log | `logcat(DEBUG)` added |
| ✅ | `feature/reader/.../ReaderPagePreProcessor.kt:176` | `catch (_: Exception)` in `checkAndFilter()` — no log | `logcat(DEBUG)` added |
| ✅ | `feature/reader/.../ReaderActivity.kt:677` | `catch (_: Exception)` in `setDisplayProfile()` — no log | `logcat(WARN)` added |
| ✅ | `feature/reader/.../ReaderViewModel.kt:1186` | inner `catch (_: Exception)` in `findMatchingBlockedHash()` — no log | `logcat(DEBUG)` added |
| ✅ | `feature/reader/.../ReaderViewModel.kt:1190` | outer `catch (_: Exception)` in `findMatchingBlockedHash()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/download/.../Downloader.kt:685` | `catch (_: Exception) { /* skip */ }` on dimension read — no log | `logcat(DEBUG)` added |
| ✅ | `core/domain/.../MigrateMangaUseCase.kt:53` | `catch (_: Exception) { // Worst case... }` on chapter sync — no log | `logcat(WARN)` added |
| ✅ | `core/common/.../ContextExtensions.kt:48` | `catch (e: Exception) { // toast(e.message) }` on `startActivity` — no log | `logcat(ERROR)` added |
| ✅ | `feature/settings/.../SettingsTrackingScreen.kt:619` | `catch (_: Exception) {}` on Jellyfin library fetch — no log | `logcat(WARN)` added |
| ✅ | `app/.../migration/.../MigrationListScreenModel.kt:160` | `catch (_: Exception) {}` on thumbnail detail fetch — no log | `logcat(WARN)` added |
| ✅ | `app/.../migration/.../MigrationListScreenModel.kt:230` | `catch (_: Exception)` on source search failure — no log | `logcat(WARN)` added |
| ✅ | `app/.../migration/.../MigrationListScreenModel.kt:276` | `catch (_: Exception)` on chapter sync in `useMangaForMigration()` — no log | `logcat(WARN)` + `CancellationException` rethrow added |
| ✅ | `feature/browse/.../SourcePreferencesScreen.kt:158` | `catch (_: Exception)` on reflection for `OnBindEditTextListener` — no log | `logcat(DEBUG)` added |
| ✅ | `feature/manga/.../TrackInfoDialog.kt:270` | `catch (_: Exception)` on `tracker.register()` — toasts user but no diagnostic log | `logcat(ERROR)` added |
| ✅ | `feature/manga/.../CoverSearchScreenModel.kt:155` | `catch (_: Exception)` on cover source search — no log | `logcat(WARN)` added |
| ✅ | `core/common/.../DataStorePreferenceStore.kt:174` | `catch (_: Exception)` on preference deserialization in `getSync()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/common/.../DataStorePreferenceStore.kt:197` | `catch (_: Exception)` on preference deserialization in `changes()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/common/.../DiskUtil.kt:58` | `catch (_: Exception)` on `StatFs` for total space — no log | `logcat(WARN)` added |
| ✅ | `core/common/.../DiskUtil.kt:82` | `catch (_: Exception)` on `StatFs` for available space — no log | `logcat(WARN)` added |
| ✅ | `eu.kanade.../WebViewInterceptor.kt:43` | `catch (_: Exception)` on `WebSettings.getDefaultUserAgent` crash — no log | `logcat(WARN)` added |
| ✅ | `core/data/.../ALFuzzyDate.kt:20` | `catch (_: Exception)` on `LocalDate.of()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/data/.../MyAnimeListApi.kt:295` | `catch (_: Exception)` on `SimpleDateFormat.format()` — no log | `logcat(DEBUG)` added |
| ✅ | `core/data/.../Bangumi.kt:122` | `catch (_: Throwable)` on login — calls `logout()` silently | `logcat(WARN)` added |
| ✅ | `core/data/.../Bangumi.kt:134` | `catch (_: Exception)` on `restoreToken` JSON parse — no log | `logcat(DEBUG)` added |
| ✅ | `core/domain/.../UpdateManga.kt:41` | `catch (_: UninitializedPropertyAccessException)` on source title read — no log | `logcat(DEBUG)` added |

### Fixed (architectural)

| Status | File | Violation | Fix Applied |
|--------|------|-----------|-------------|
| ✅ | `core/data/.../track/kavita/KavitaInterceptor.kt:13` | `runBlocking { kavita.loadOAuth() }` inside OkHttp interceptor — concurrent requests all block and each triggers a duplicate load | `Mutex` double-checked-locking added: only the first waiter calls `loadOAuth()`; subsequent callers skip after acquiring the lock and seeing `authentications != null` |

---

## Principle 5 — Everything Observable Must Be Testable in Isolation

Dependencies that must become interfaces before unit-testing is possible:

| Status | Class | Status | Target |
|--------|-------|--------|--------|
| ✅ | `CategoryRepository` | Interface | — |
| ✅ | `MangaRepository` | Interface | — |
| ✅ | `DownloadManager` | Interface (`domain.download.service`) | — |
| ✅ | `TrackerManager` | Interface (`domain.track.service`) | — |
| ⏳ | `NetworkHelper` | Concrete class | Extract `NetworkClient` interface in domain |
| ⏳ | `ExtensionManager` | Concrete + alias | Formalise pure-Kotlin domain interface |

---

## Principle 6 — Startup Is a Contract, Not a Hope

| Status | Item | Gap | Fix Applied / Required |
|--------|------|-----|------------------------|
| ✅ | Koin startup wrapped in try/catch | Failures were invisible | `recordError(KOIN_INITIALIZED)` before re-throw |
| ✅ | `initializeMigrator()` crash-safe | Migrator throw left splash on forever | try/catch records error + completes phase + fallback init |
| ✅ | Theme/log-level `.getSync()` guarded | DataStore race on first launch | try/catch with safe defaults |
| ✅ | `WORKMANAGER_CONFIGURED` phase added | No startup visibility for WorkManager init | Phase added to enum; `complete()` called inside `workManagerConfiguration` getter — fires the first time WorkManager requests its `Configuration` |
| ✅ | Time-bound all phases | Only `MIGRATOR_COMPLETE` (30 s) and overlay (10 s) had timeouts | `timeoutMs: Long` added to each `Phase` enum entry; `StartupDiagnosticOverlay` now shows `Warning` icon + "OVERDUE (>Ns)" label in amber for any pending phase that has exceeded its individual budget |

---

## Principle 7 — Dependency Injection Is Constructor Injection

| Status | Module | Violation | Required Fix |
|--------|--------|-----------|--------------|
| ⏳ | `app/build.gradle.kts` | `koinCompiler { compileSafety.set(false) }` | Enable `compileSafety.set(true)` after converting manual modules to `@Module` annotations or providing `@ExternalDefinitions` |
| ⏳ | `feature/more/build.gradle.kts` | Same | Same |
| ⏳ | `feature/library/build.gradle.kts` | Same | Same |
| ⏳ | `feature/browse/build.gradle.kts` | Same | Same |
| ⏳ | `feature/updates/build.gradle.kts` | Same | Same |
| ⏳ | `feature/manga/build.gradle.kts` | Same | Same |
| ⏳ | `feature/stats/build.gradle.kts` | Same | Same |

---

## Principle 8 — State Has Exactly One Representation at Each Layer

| Status | File | Violation | Fix Applied / Required |
|--------|------|-----------|------------------------|
| ✅ | `feature/browse/.../ExtensionsScreenModel.kt` | `currentDownloads: MutableStateFlow` independent of `mutableState` | Merged `currentDownloads` into `ExtensionsScreenModel.State` |
| ✅ | `feature/browse/.../AuthoritySearchScreenModel.kt` | `allTrackers: MutableStateFlow` independent of `mutableState` | Merged into `AuthoritySearchState.availableTrackers` |

---

## CI Fitness Functions (already implemented)

| Status | Check | Enforced In |
|--------|-------|-------------|
| ✅ | No `android.*` import in `:core:domain` or `:domain` source | `build.yml` — "Architecture fitness – no android.* imports" step |
| ✅ | No `Injekt.get()` outside legacy shim | `build.yml` — "Architecture fitness – no Injekt.get()" step |
| ✅ | `ephyra.data.*` import count in `feature/` must not exceed baseline of 41 | `build.yml` — "Architecture fitness – data-boundary ratchet" step; reduces to a build error if new violations are added |

---

## How to Use This Checklist

1. Pick the highest-priority **⏳ Pending** item.
2. Create a branch, implement the fix.
3. Run `./gradlew :app:compileDebugKotlin` — the CI fitness functions will
   catch domain-layer regressions automatically.
4. Change the row to **✅ Fixed** in the same commit.
5. Never merge a PR that introduces a new violation without also adding a
   corresponding row (or fixing it in the same change).

*This document is a living audit — update it as violations are resolved or
newly discovered.*
