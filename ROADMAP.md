# Ephyra Roadmap & Modernization Guide

The re-architecture of Ephyra represents a shift from a decade of technical debt—characterized by
tightly coupled "God Objects" and synchronous data access—to a modern, enterprise-grade mobile
system. This evolution is guided by a **"Heal to Enable Selection"** philosophy, ensuring that core
components can be selectively replaced in the future without destabilizing the entire platform.

This directory contains the documentation guiding our transition from the legacy infrastructure to a
fully compliant, modernized state.

## ✅ Completed — Modernization Sprint

The following foundational work has been completed and merged:

- **Module-boundary architecture**: Feature modules depend only on domain interfaces, not concrete
  `:app` implementations. Cross-module contracts flow through `presentation-core` and `domain`
  packages.
- **Koin 4.2.1 migration**: All internal `Injekt.get()` calls replaced with constructor injection.
  The `uy.kohesive.injekt.Injekt` shim is preserved for legacy extension compatibility.
- **Resource decoupling**: All R-resource references corrected to their proper module aliases.
  Shared UI components (e.g., `AppStateBanners`) extracted to `presentation-core`.
- **Preference flow modernization**: Remaining `.get()` suspend calls in non-coroutine contexts
  replaced with `.getSync()` or `Preference.collectAsState()` patterns.
- **Full build success**: `:app:compileDebugKotlin` **BUILD SUCCESSFUL** ✅
- **Spotless lint clean**: All modules pass `spotlessCheck` with no violations.

## ✅ Completed — Startup Hardening & Architectural Fitness

- **Koin startup hardened**: `startKoin {}` is wrapped in try/catch; failures are recorded via
  `StartupTracker.recordError()` and re-thrown to surface in `CrashActivity` rather than silently
  degrading.
- **Module order corrected**: `koinPreferenceModule` now loads before `koinDomainModule`,
  ensuring preference bindings are always registered before domain singletons can reference them.
- **`initializeMigrator()` crash-safe**: The coroutine body is wrapped in try/catch; exceptions
  call `StartupTracker.recordError()`, complete `MIGRATOR_STARTED`, and fall back to
  `Migrator.initialize(old=0)` to unblock `Migrator.await()` in `MainActivity`.
- **Defensive `.getSync()` reads**: Theme and log-level preference reads at startup are guarded
  with try/catch and safe defaults, preventing a DataStore race condition from surfacing as a
  crash rather than a harmless visual default.
- **`HomeScreen` channel strategies fixed**: `librarySearchEvent` and `openTabEvent` now use
  `Channel.CONFLATED`, preventing stale navigation intents from accumulating on the singleton
  screen across configuration changes or rapid back-stack cycles.
- **Explicit `R` import in `App.kt`**: `import ephyra.app.R` added explicitly to remove
  ambiguity around R-class resolution in multi-module configurations.
- **Architectural fitness functions in CI**: Two new steps in `build.yml` fail the build if
  (a) `android.*` imports appear in domain module source trees, or (b) `Injekt.get()` appears
  anywhere outside the legacy shim. These run in milliseconds and prevent regressions silently
  creeping back in.
- **Design Principles document**: `doc/DESIGN_PRINCIPLES.md` created as the authoritative,
  verbalisable law of the codebase — eight principles with canonical anti-patterns and a guiding
  code-review question.

## Core Documentation

1. **[Design Principles](doc/DESIGN_PRINCIPLES.md)**: The authoritative law of the codebase.
   Eight concrete principles — with canonical anti-patterns and a guiding code-review question —
   that every developer must read before contributing. This document supersedes any informal
   conventions previously used in the codebase.
2. **[Architecture Principles](doc/ARCHITECTURE.md)**: Technical details of the design patterns
   and architectural rules governing the modernized state of Ephyra (Hilt, Room, UDF, Domain
   Interactors).
3. **[Migration Plan](doc/MIGRATION_PLAN.md)**: A structured roadmap outlining the phased
   transition. Use this document to track progress and identify the current active phase of
   modernization.
4. **[Compile-Time Safety Plan](doc/COMPILE_SAFETY_PLAN.md)**: Detailed plan for migrating
   from Koin (runtime DI) to Hilt (compile-time DI) and completing the Room/SQLDelight
   consolidation.  This is the **active priority for Phase 10**.
5. **[Validation Criteria (Definition of Done)](doc/VALIDATION_CRITERIA.md)**: Establishing the
   testable metrics for when a modernization phase or architectural pattern is considered completely
   migrated.

## Next Steps

The following phases are the active next priorities. See `doc/MIGRATION_PLAN.md` for details.

- **Phase 10 (ACTIVE)** — Compile-Time Safety: Replace Koin with Hilt (compile-time DI
  graph validation), retire SQLDelight, and add CI enforcement gates.  A green build must
  equal a running app — no more runtime `NoBeanDefFoundException` crashes.
  See [`doc/COMPILE_SAFETY_PLAN.md`](doc/COMPILE_SAFETY_PLAN.md) for the full phased plan.
- **Phase 4** — Business Logic Isolation: Break down "God Object" repositories into single-purpose
  Domain Interactors.
- **Phase 6** — Database Engine: Complete SQLDelight retirement (tracked in Phase 10-G).

---

**Summary of Future Direction**: By rejecting "in-place" substitutions and instead rewriting
paradigms, the project is moving toward a state of **Structural Weightlessness**. The eventual goal
is a modularized repository where features are isolated, making the codebase intuitive, navigable,
and resistant to technical debt.
