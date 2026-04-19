package ephyra.feature.manga

import android.app.Application
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.saver.Image
import ephyra.core.common.saver.ImageSaver
import ephyra.core.common.saver.Location
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.source.service.SourceManager
import ephyra.i18n.MR
import ephyra.presentation.core.util.manga.editCover
import ephyra.presentation.core.util.system.encoder
import ephyra.presentation.core.util.system.getBitmapOrNull
import ephyra.source.local.image.LocalCoverManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.Request
import org.koin.core.annotation.Factory
import org.koin.core.annotation.InjectedParam

@Factory
class MangaCoverScreenModel(
    @InjectedParam private val mangaId: Long,
    private val getManga: GetManga,
    private val imageSaver: ImageSaver,
    private val coverCache: CoverCache,
    private val updateManga: UpdateManga,
    private val libraryPreferences: LibraryPreferences,
    private val sourceManager: SourceManager,
    private val networkHelper: NetworkHelper,
    private val application: Application,
    private val localCoverManager: LocalCoverManager,
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<Manga?>(null) {

    private val effectChannel = Channel<MangaCoverEffect>(Channel.BUFFERED)

    /** One-shot UI side-effects to be collected by the composable. */
    val effectFlow = effectChannel.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            getManga.subscribe(mangaId)
                .collect { newManga -> mutableState.update { newManga } }
        }
    }

    fun onEvent(event: MangaCoverScreenEvent) {
        when (event) {
            is MangaCoverScreenEvent.SaveCover -> saveCover()
            is MangaCoverScreenEvent.ShareCover -> shareCover()
            is MangaCoverScreenEvent.EditCover -> editCover(event.data)
            is MangaCoverScreenEvent.DeleteCustomCover -> deleteCustomCover()
            is MangaCoverScreenEvent.SetCoverFromUrl -> setCoverFromUrl(event.coverUrl, event.sourceId)
        }
    }

    private fun saveCover() {
        screenModelScope.launch {
            try {
                saveCoverInternal(temp = false)
                snackbarHostState.showSnackbar(
                    application.stringResource(MR.strings.cover_saved),
                    withDismissAction = true,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    application.stringResource(MR.strings.error_saving_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    private fun shareCover() {
        screenModelScope.launch {
            try {
                val uri = saveCoverInternal(temp = true) ?: return@launch
                // Emit effect — the UI layer handles startActivity with Activity context
                effectChannel.send(MangaCoverEffect.StartShare(uri))
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    application.stringResource(MR.strings.error_sharing_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    /**
     * Save manga cover Bitmap to picture or temporary share directory.
     *
     * When sharing (temp = true), always uses PNG for universal compatibility.
     * When saving to Pictures, uses the user's preferred [LibraryPreferences.ImageFormat]
     * (WebP lossless by default) for efficient storage.
     *
     * @return the uri to saved file
     */
    private suspend fun saveCoverInternal(temp: Boolean): Uri? {
        val manga = state.value ?: return null
        val req = ImageRequest.Builder(application)
            .data(manga)
            .size(Size.ORIGINAL)
            .build()

        // Shares always use PNG for compatibility; internal saves use user's preferred format
        val encoder: (android.graphics.Bitmap, java.io.OutputStream) -> Unit = if (temp) {
            { bmp, os -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os) }
        } else {
            libraryPreferences.imageFormat().get().encoder()
        }

        return withIOContext {
            val result = application.imageLoader.execute(req).image?.asDrawable(application.resources)

            // TODO: Handle animated cover
            val bitmap = result?.getBitmapOrNull() ?: return@withIOContext null
            imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    name = manga.title,
                    location = if (temp) Location.Cache else Location.Pictures.create(),
                    encoder = encoder,
                ),
            )
        }
    }

    /**
     * Update cover with local file.
     *
     * @param data uri of the cover resource.
     */
    private fun editCover(data: Uri) {
        val manga = state.value ?: return
        screenModelScope.launchIO {
            application.contentResolver.openInputStream(data)?.use {
                try {
                    manga.editCover(localCoverManager, it, updateManga, coverCache)
                    notifyCoverUpdated()
                } catch (e: Exception) {
                    notifyFailedCoverUpdate(e)
                }
            }
        }
    }

    private fun deleteCustomCover() {
        val id = state.value?.id ?: return
        screenModelScope.launchIO {
            try {
                coverCache.deleteCustomCover(id)
                updateManga.awaitUpdateCoverLastModified(id)
                notifyCoverUpdated()
            } catch (e: Exception) {
                notifyFailedCoverUpdate(e)
            }
        }
    }

    /**
     * Set cover from a URL by downloading the image and saving it as a custom cover.
     * The raw bytes from the source are always streamed directly to preserve the original
     * format. The [ImageFormat] preference only affects *derived* images (splits, merges,
     * save/share); custom covers are stored as received to maintain byte-for-byte fidelity.
     *
     * Uses [sourceId] to look up the [HttpSource] and apply its headers/client so that
     * sources requiring Referer/User-Agent/cookies work correctly.
     *
     * @param coverUrl URL of the cover image to download.
     * @param sourceId ID of the source that owns this cover URL.
     */
    private fun setCoverFromUrl(coverUrl: String, sourceId: Long) {
        val manga = state.value ?: return
        screenModelScope.launchIO {
            try {
                val source = sourceManager.get(sourceId)
                val httpSource = source as? HttpSource
                val client = httpSource?.client ?: networkHelper.client
                val request = Request.Builder()
                    .url(coverUrl)
                    .apply { httpSource?.headers?.let { headers(it) } }
                    .build()
                val response = client.newCall(request).await()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Failed to download cover: ${resp.code}")
                    }
                    resp.body.byteStream().use { input ->
                        manga.editCover(localCoverManager, input, updateManga, coverCache)
                    }
                }
                notifyCoverUpdated()
            } catch (e: Exception) {
                notifyFailedCoverUpdate(e)
            }
        }
    }

    private fun notifyCoverUpdated() {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                application.stringResource(MR.strings.cover_updated),
                withDismissAction = true,
            )
        }
    }

    private fun notifyFailedCoverUpdate(e: Throwable) {
        screenModelScope.launch {
            snackbarHostState.showSnackbar(
                application.stringResource(MR.strings.notification_cover_update_failed),
                withDismissAction = true,
            )
            logcat(LogPriority.ERROR, e)
        }
    }
}
