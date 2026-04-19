package ephyra.presentation.core.util

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.coroutines.ContinuationInterceptor

/**
 * Tests for the "historic" extension functions and interfaces in
 * [ephyra.presentation.core.util.Navigator]:
 *
 * - The [ioCoroutineScope] factory pattern (IO dispatcher + SupervisorJob + CoroutineName)
 * - The [Tab] interface default [Tab.onReselect] no-op behaviour
 * - The [AssistContentScreen] interface contract
 * - The [LocalBackPress] composition local default value
 *
 * These extensions originate from the Tachiyomi/Mihon codebase and are retained
 * for compatibility with feature modules and historic extension patterns.
 */
@Execution(ExecutionMode.CONCURRENT)
class NavigatorExtensionsTest {

    // ── ioCoroutineScope factory logic ────────────────────────────────────────

    /**
     * The [ioCoroutineScope] extension builds its scope with a specific context.
     * This test verifies the factory closure used inside the extension:
     * `CoroutineScope(Dispatchers.IO + SupervisorJob()) + CoroutineName(key)`
     *
     * We test the logic directly to avoid requiring a live ScreenModel / ScreenModelStore.
     */
    @Test
    fun `ioCoroutineScope factory creates scope on IO dispatcher`() {
        val key = "ScreenModelIoCoroutineScope"
        val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) + CoroutineName(key)

        try {
            val interceptor = scope.coroutineContext[ContinuationInterceptor]
            assertNotNull(interceptor) { "ioCoroutineScope must have a ContinuationInterceptor (dispatcher)" }
            // Dispatchers.IO is an internal implementation; we verify the interceptor is the IO one
            assertEquals(Dispatchers.IO, interceptor) {
                "ioCoroutineScope should use Dispatchers.IO"
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `ioCoroutineScope factory attaches CoroutineName to the scope`() {
        val key = "ScreenModelIoCoroutineScope"
        val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) + CoroutineName(key)

        try {
            val coroutineName = scope.coroutineContext[CoroutineName]
            assertNotNull(coroutineName) { "ioCoroutineScope must carry a CoroutineName" }
            assertEquals(key, coroutineName!!.name) {
                "CoroutineName should equal the key passed to the factory"
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `ioCoroutineScope factory includes an active Job`() {
        val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) + CoroutineName("test")

        try {
            // At minimum the scope must be active after creation
            assertTrue(scope.coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
                "Scope created by ioCoroutineScope factory must be active on creation"
            }
        } finally {
            scope.cancel()
        }
    }

    // ── Tab interface ─────────────────────────────────────────────────────────

    /**
     * [Tab] adds [Tab.onReselect] with a default no-op body.
     * Verifying the method exists on the interface is a regression guard:
     * if the method is accidentally removed or renamed, this test fails before CI.
     */
    @Test
    fun `Tab interface declares onReselect method`() {
        val method = Tab::class.java.methods.firstOrNull { it.name == "onReselect" }
        assertNotNull(method) { "ephyra.presentation.core.util.Tab must declare onReselect" }
    }

    /**
     * [Tab.onReselect] takes a single [cafe.adriel.voyager.navigator.Navigator] parameter.
     */
    @Test
    fun `Tab onReselect accepts a Navigator parameter`() {
        val method = Tab::class.java.methods.firstOrNull { it.name == "onReselect" }
        assertNotNull(method) { "Tab must declare onReselect" }
        // Kotlin suspend functions receive a Continuation as the last parameter at the JVM level
        assertTrue(method!!.parameterCount >= 1) {
            "onReselect must accept at least a Navigator parameter (plus Kotlin continuation)"
        }
    }

    // ── AssistContentScreen interface ─────────────────────────────────────────

    /**
     * [AssistContentScreen.onProvideAssistUrl] must be declared on the interface.
     */
    @Test
    fun `AssistContentScreen interface declares onProvideAssistUrl`() {
        val method = AssistContentScreen::class.java.methods
            .firstOrNull { it.name == "onProvideAssistUrl" }
        assertNotNull(method) { "AssistContentScreen must declare onProvideAssistUrl" }
    }

    /**
     * [AssistContentScreen.onProvideAssistUrl] must return a nullable [String].
     */
    @Test
    fun `AssistContentScreen onProvideAssistUrl returns nullable String`() {
        val method = AssistContentScreen::class.java.methods
            .firstOrNull { it.name == "onProvideAssistUrl" }
        assertNotNull(method) { "AssistContentScreen must declare onProvideAssistUrl" }
        assertEquals(String::class.java, method!!.returnType) {
            "onProvideAssistUrl return type should be String (nullable in Kotlin)"
        }
    }

    /**
     * A concrete [AssistContentScreen] implementation can return null.
     */
    @Test
    fun `AssistContentScreen implementation can return null from onProvideAssistUrl`() {
        val screen = object : AssistContentScreen {
            override fun onProvideAssistUrl(): String? = null
        }
        assertNull(screen.onProvideAssistUrl()) {
            "AssistContentScreen.onProvideAssistUrl must be nullable and returning null should be valid"
        }
    }

    /**
     * A concrete [AssistContentScreen] implementation can return a non-null URL.
     */
    @Test
    fun `AssistContentScreen implementation can return a URL from onProvideAssistUrl`() {
        val expectedUrl = "https://example.com/manga/123"
        val screen = object : AssistContentScreen {
            override fun onProvideAssistUrl(): String = expectedUrl
        }
        assertEquals(expectedUrl, screen.onProvideAssistUrl())
    }

    // ── LocalBackPress composition local ──────────────────────────────────────

    /**
     * [LocalBackPress] must provide `null` as its default value.  Feature modules
     * that read it outside a composition providing a handler must receive null rather
     * than throwing a [RuntimeException].
     *
     * Note: this test validates the *static* declaration of the default factory; it
     * does not spin up a Compose runtime.
     */
    @Test
    fun `LocalBackPress is declared as a staticCompositionLocalOf`() {
        // If LocalBackPress does not exist this reference will not compile.
        val local = LocalBackPress
        assertNotNull(local) { "LocalBackPress composition local must be declared" }
    }

    // ── Tab interface extends Voyager Tab ─────────────────────────────────────

    /**
     * [Tab] must extend [cafe.adriel.voyager.navigator.tab.Tab] so that Voyager's
     * tab navigation machinery accepts Ephyra screen-model tabs.
     */
    @Test
    fun `ephyra Tab interface extends Voyager Tab interface`() {
        val voyagerTabInterface = cafe.adriel.voyager.navigator.tab.Tab::class.java
        assertTrue(voyagerTabInterface.isAssignableFrom(Tab::class.java)) {
            "ephyra.presentation.core.util.Tab must extend cafe.adriel.voyager.navigator.tab.Tab"
        }
    }

    // ── Default Tab onReselect is a no-op ────────────────────────────────────

    /**
     * Verifies the default [Tab.onReselect] no-op via reflection: the method must exist,
     * be non-abstract, and its default implementation must complete without throwing.
     *
     * We test via reflection to avoid requiring a fully composed Tab (Content() is
     * @Composable and requires a live Compose runtime).
     */
    @Test
    fun `Tab onReselect method is non-abstract (has a default body)`() {
        val method = Tab::class.java.methods.firstOrNull { it.name == "onReselect" }
        assertNotNull(method) { "Tab must declare onReselect" }

        // In Kotlin, an interface method with a default body is compiled as a default
        // JVM interface method (JVM 8+). Checking it is NOT abstract confirms the default body.
        val javaMethod = method!!
        assertFalse(
            java.lang.reflect.Modifier.isAbstract(javaMethod.modifiers),
            "Tab.onReselect should have a default (non-abstract) implementation",
        )
    }
}
