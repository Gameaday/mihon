package ephyra.app.util

import ephyra.presentation.core.util.AppNavigator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Contract tests for [AppNavigator] and its concrete implementation [NavigatorImpl].
 *
 * These tests validate:
 * - [NavigatorImpl] correctly implements [AppNavigator]
 * - The [AppNavigator] interface exposes the expected API surface
 * - [NavigatorImpl] can be instantiated without Android context
 *
 * Interface compliance is verified at both the compile-time level (the source compiles)
 * and at runtime via Java reflection.
 */
@Execution(ExecutionMode.CONCURRENT)
class AppNavigatorContractTest {

    // ── Compile-time interface adherence ──────────────────────────────────────

    /**
     * Assigns [NavigatorImpl] to an [AppNavigator] reference to confirm the compiler
     * accepts the assignment (i.e. the interface is correctly implemented).
     */
    @Test
    fun `NavigatorImpl is assignable to AppNavigator`() {
        val navigator: AppNavigator = NavigatorImpl()
        assertNotNull(navigator)
    }

    // ── Runtime type checks ───────────────────────────────────────────────────

    @Test
    fun `NavigatorImpl class implements AppNavigator`() {
        assertTrue(AppNavigator::class.java.isAssignableFrom(NavigatorImpl::class.java)) {
            "NavigatorImpl must implement the AppNavigator interface"
        }
    }

    @Test
    fun `NavigatorImpl is not the AppNavigator interface itself`() {
        // Sanity check: the concrete class must differ from the interface
        assertTrue(NavigatorImpl::class.java != AppNavigator::class.java)
    }

    // ── Constructor / instantiation ───────────────────────────────────────────

    /**
     * Confirms that [NavigatorImpl] has a public no-arg constructor so that Koin's
     * `single { NavigatorImpl() }` factory can create it without any Koin-provided
     * dependencies.
     */
    @Test
    fun `NavigatorImpl can be instantiated without arguments`() {
        val instance = NavigatorImpl()
        assertNotNull(instance)
        assertTrue(instance is AppNavigator)
    }

    // ── API surface ───────────────────────────────────────────────────────────

    /**
     * Checks that [AppNavigator] declares [AppNavigator.openMangaScreen] with the
     * correct parameter types.
     */
    @Test
    fun `AppNavigator declares openMangaScreen with Context and Long parameters`() {
        val method = AppNavigator::class.java.methods.firstOrNull { it.name == "openMangaScreen" }

        assertNotNull(method) { "AppNavigator must declare openMangaScreen" }
        assertEquals(2, method!!.parameterCount) { "openMangaScreen should have 2 parameters" }
        assertEquals(android.content.Context::class.java, method.parameterTypes[0])
        assertEquals(Long::class.javaPrimitiveType, method.parameterTypes[1])
    }

    /**
     * Checks that [AppNavigator] declares [AppNavigator.openWebView] with the
     * correct parameter types.
     */
    @Test
    fun `AppNavigator declares openWebView with Context, String, Long and String parameters`() {
        val method = AppNavigator::class.java.methods.firstOrNull { it.name == "openWebView" }

        assertNotNull(method) { "AppNavigator must declare openWebView" }
        assertEquals(4, method!!.parameterCount) { "openWebView should have 4 parameters" }
        assertEquals(android.content.Context::class.java, method.parameterTypes[0])
        assertEquals(String::class.java, method.parameterTypes[1])
        assertEquals(Long::class.javaPrimitiveType, method.parameterTypes[2])
        assertEquals(String::class.java, method.parameterTypes[3])
    }

    /**
     * Confirms that [NavigatorImpl] provides concrete implementations of both
     * [AppNavigator] methods (i.e. it is not an abstract class).
     */
    @Test
    fun `NavigatorImpl provides concrete implementations of all AppNavigator methods`() {
        val navigatorImplClass = NavigatorImpl::class.java

        val openManga = navigatorImplClass.methods.firstOrNull { it.name == "openMangaScreen" }
        val openWebView = navigatorImplClass.methods.firstOrNull { it.name == "openWebView" }

        assertNotNull(openManga) { "NavigatorImpl must override openMangaScreen" }
        assertNotNull(openWebView) { "NavigatorImpl must override openWebView" }

        // Neither method should be abstract
        assertFalse(
            java.lang.reflect.Modifier.isAbstract(openManga!!.modifiers),
            "openMangaScreen in NavigatorImpl must not be abstract",
        )
        assertFalse(
            java.lang.reflect.Modifier.isAbstract(openWebView!!.modifiers),
            "openWebView in NavigatorImpl must not be abstract",
        )
    }
}
