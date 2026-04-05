package ephyra.presentation.core.util

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

abstract class Screen : VoyagerScreen {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    abstract override fun Content()
}
