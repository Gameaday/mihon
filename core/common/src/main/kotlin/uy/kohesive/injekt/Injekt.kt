package uy.kohesive.injekt

import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.qualifier
import kotlin.reflect.KClass

/**
 * A legacy compatibility shim for extensions that still rely on the Injekt service locator.
 * Directs all [get] calls to the modern Koin [GlobalContext].
 */
object Injekt {

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> get(
        qualifier: String? = null,
        noinline parameters: (() -> Any)? = null,
    ): T {
        return GlobalContext.get().get(
            clazz = T::class,
            qualifier = qualifier?.let { qualifier(it) },
            parameters = parameters?.let { { parametersOf(it()) } },
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(
        clazz: KClass<T>,
        qualifier: String? = null,
        noinline parameters: (() -> Any)? = null,
    ): T {
        return GlobalContext.get().get(
            clazz = clazz,
            qualifier = qualifier?.let { qualifier(it) },
            parameters = parameters?.let { { parametersOf(it()) } },
        )
    }
}
