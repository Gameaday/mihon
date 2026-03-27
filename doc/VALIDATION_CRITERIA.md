# Ephyra Validation Criteria (Definition of Done)

To successfully finalize the modernization of Ephyra, we must validate each area of the codebase against strict criteria to ensure the architecture is fully implemented and technical debt is resolved.

## Dependency Injection: Complete Conversion to Koin
*   **Metric**: 0 instances of `Injekt.get()` across the internal codebase (excluding the legacy compatibility shim itself).
*   **Metric**: 0 instances of `KoinJavaComponent.get()`.
*   **Metric**: No ad-hoc inline `KoinComponent` objects used as Service Locators.
*   **Validation**: Search query `grep -rn "Injekt.get" .` and `grep -rn "KoinJavaComponent.get" .` return zero results in internal code. All application services, view models, and interactor dependencies are injected strictly via constructor injection. The `uy.kohesive.injekt.Injekt` shim must remain intact for external extensions.

## Synchronous Preferences: Complete Conversion to DataStore
*   **Metric**: 0 instances of blocking disk I/O on the Main thread for preferences.
*   **Validation**: All interactions with persistent settings utilize AndroidX DataStore (`DataStorePreferenceStore`) and are accessed asynchronously via `Flow`. The legacy `AndroidPreferenceStore` is entirely removed from the codebase.
*   **Validation**: The application runs completely free of `StrictMode` disk read/write violations on the main thread during navigation and initial startup.

## Business Logic: Full Interactor Compliance
*   **Metric**: 0 "God Object" Repositories injected directly into `ScreenModel`s.
*   **Validation**: The Presentation layer (`ScreenModel`s) solely injects single-purpose Domain Interactors (e.g., `GetManga`, `UpdateReadStatus`). The presentation layer does not handle complex data fetching or merging logic.

## State Management: Strict Unidirectional Data Flow (UDF)
*   **Metric**: Every `ScreenModel` exposes exactly one immutable `ViewState`.
*   **Validation**: All interactions from the UI to the `ScreenModel` are routed through a single `onEvent(event)` intent method.
*   **Validation**: There are no multiple independent data streams or raw mutables exposed directly to the compose UI layer.

## Database: Complete Conversion to Room
*   **Metric**: 0 occurrences of SQLDelight queries handling core application logic.
*   **Validation**: Room is the sole database engine. Database observation relies on Room’s native `Flow` and `Paging 3` integration. All necessary database migrations from the legacy SQLDelight schema to Room have automated test coverage.

## API Compatibility: Preserved Extension Namespace
*   **Metric**: `eu.kanade.tachiyomi.*` namespace remains intact for all public extension APIs.
*   **Validation**: The `source-api` module compiles and functions without issue using existing extensions. R8/Proguard rules successfully protect shared libraries (`okhttp3`, `jsoup`) and do not strip necessary classes required by the dynamic APK plugins.
