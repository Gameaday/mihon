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

## Core Documentation

1. **[Architecture Principles](doc/ARCHITECTURE.md)**: Details the philosophies, design patterns,
   and architectural rules governing the modernized state of Ephyra (e.g., Koin Dependency
   Injection, Room Database, UDF, Domain Interactors).
2. **[Migration Plan](doc/MIGRATION_PLAN.md)**: A structured roadmap outlining the phased
   transition. Use this document to track progress and identify the current active phase of
   modernization.
3. **[Validation Criteria (Definition of Done)](doc/VALIDATION_CRITERIA.md)**: Establishing the
   testable metrics for when a modernization phase or architectural pattern is considered completely
   migrated.

## Next Steps

The following phases are the active next priorities. See `doc/MIGRATION_PLAN.md` for details.

- **Phase 4** — Business Logic Isolation: Break down "God Object" repositories into single-purpose
  Domain Interactors.
- **Phase 5** — UI Architecture Stabilization: Enforce strict Unidirectional Data Flow in all
  `ScreenModel`s.
- **Phase 6** — Database Engine: Migrate from SQLDelight to Room.
- **Koin Graph Safety**: Once a future Koin Annotations release includes `@ExternalDefinitions`,
  replace the current `compileSafety.set(false)` approach with explicit external definition
  annotations per feature module.

---

**Summary of Future Direction**: By rejecting "in-place" substitutions and instead rewriting
paradigms, the project is moving toward a state of **Structural Weightlessness**. The eventual goal
is a modularized repository where features are isolated, making the codebase intuitive, navigable,
and resistant to technical debt.
