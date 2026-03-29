package ephyra.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.LifecycleCoroutineScope
import ephyra.core.common.core.security.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import ephyra.core.common.util.system.logcat
import ephyra.domain.updates.interactor.GetUpdates

class WidgetManager(
    private val getUpdates: GetUpdates,
    private val securityPreferences: SecurityPreferences,
) {

    fun Context.init(scope: LifecycleCoroutineScope) {
        combine(
            getUpdates.subscribe(read = false, after = BaseUpdatesGridGlanceWidget.DateLimit.toEpochMilli()),
            securityPreferences.useAuthenticator().changes(),
            transform = { a, b -> a to b },
        )
            .distinctUntilChanged { old, new ->
                old.second == new.second &&
                    old.first.size == new.first.size &&
                    old.first.mapTo(HashSet(old.first.size)) { it.chapterId } ==
                    new.first.mapTo(HashSet(new.first.size)) { it.chapterId }
            }
            .onEach {
                try {
                    UpdatesGridGlanceWidget().updateAll(this)
                    UpdatesGridCoverScreenGlanceWidget().updateAll(this)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to update widget" }
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(scope)
    }
}
