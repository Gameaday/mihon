package ephyra.core.migration

import org.koin.core.context.GlobalContext

class MigrationContext(val dryrun: Boolean) {

    inline fun <reified T : Any> get(): T? {
        return GlobalContext.getOrNull()?.getOrNull<T>()
    }
}
