package ephyra.presentation.core.ui

import cafe.adriel.voyager.core.screen.Screen

/**
 * Factory for creating an onboarding screen without introducing a circular dependency.
 *
 * [feature:settings] cannot import [OnboardingScreen] directly because it lives in
 * [feature:more], and [feature:more] transitively depends on [feature:settings].
 * Koin-inject this factory instead; [feature:more] (or [:app]) provides the binding.
 *
 * Usage:
 * ```kotlin
 * val onboardingFactory = koinInject<OnboardingScreenFactory>()
 * navigator.push(onboardingFactory.create())
 * ```
 */
fun interface OnboardingScreenFactory {
    fun create(): Screen
}
