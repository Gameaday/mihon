package ephyra.app.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ephyra.app.data.updater.AppUpdateDownloadJob
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.Screen

class NewUpdateScreen(
    private val versionName: String,
    private val changelogInfo: String,
    private val releaseLink: String,
    private val downloadLink: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val changelogInfoNoChecksum = remember {
            changelogInfo.replace("""---(\R|.)*Checksums(\R|.)*""".toRegex(), "")
        }

        NewUpdateScreen(
            versionName = versionName,
            changelogInfo = changelogInfoNoChecksum,
            onOpenInBrowser = { context.openInBrowser(releaseLink) },
            onRejectUpdate = navigator::pop,
            onAcceptUpdate = {
                AppUpdateDownloadJob.start(
                    context = context,
                    url = downloadLink,
                    title = versionName,
                )
                navigator.pop()
            },
        )
    }
}
