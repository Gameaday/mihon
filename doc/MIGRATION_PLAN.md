# Ephyra Migration Plan

This document outlines the phased approach to fully modernize the Ephyra codebase.

> **Current Status (as of modernization sprint `copilot/resolve-build-issues`):**
> Phases 1–3 are **complete**. The full `:app:compileDebugKotlin` build passes successfully.
> Spotless lint is clean across all modules. Koin is at **4.2.1**. Legacy extension API
> compatibility is preserved. Phases 4–6 are the active next steps.

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

- [ ] Audit massive "God Object" Repositories and `ScreenModel`s.
- [ ] Break down Repositories into focused, single-purpose Domain Interactors (e.g., `GetManga`,
  `UpdateReadStatus`).
- [ ] Refactor `ScreenModel`s to rely exclusively on Interactors for data mutations and retrievals.

## Phase 5: UI Architecture Stabilization (UDF)

Ensure all screens adhere to Unidirectional Data Flow.

- [ ] Ensure every `ScreenModel` exposes exactly one immutable `ViewState`.
- [ ] Ensure all UI actions map to a single `onEvent(event)` intent method.
- [ ] Remove side-effects directly modifying UI state outside of the `ViewState` pipeline.

## Phase 6: Database Engine (SQLDelight to Room)

Replace the legacy SQL-first engine with Entity-DAO Room paradigm.

- [ ] Design the Room `Entity` schema mimicking existing SQLDelight tables.
- [ ] Implement `DAO`s with `Flow` and `Paging 3` integration.
- [ ] Implement robust migration paths from the existing SQLite db to the new Room setup.
- [ ] Remove SQLDelight dependency and manual SQL queries where applicable.

## Phase 7: Host-Extension API Verification ✅

Ensure modernizations do not break existing extensions.

- [x] Verify `eu.kanade.tachiyomi.source` package namespace is intact within the `source-api`
  module.
- [x] Verify network interceptors compatibility — `internal` visibility changes are scoped to the
  `:app` module boundary and do not affect the public extension API surface.
- [ ] Validate Proguard rules protect `okhttp3`, `jsoup`, and other shared extension APIs from
  stripping or obfuscation.
