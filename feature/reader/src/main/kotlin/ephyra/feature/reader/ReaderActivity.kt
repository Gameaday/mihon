package ephyra.feature.reader

import android.annotation.SuppressLint
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.hippo.unifile.UniFile
import dev.icerock.moko.resources.StringResource
import ephyra.core.common.Constants
import ephyra.core.common.notification.NotificationManager
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.core.common.util.lang.withUIContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.base.BasePreferences
import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.feature.reader.R
import ephyra.feature.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import ephyra.feature.reader.ReaderViewModel.SetAsCoverResult.Error
import ephyra.feature.reader.ReaderViewModel.SetAsCoverResult.Success
import ephyra.feature.reader.databinding.ReaderActivityBinding
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.model.ViewerChapters
import ephyra.feature.reader.setting.ReaderSettingsScreenModel
import ephyra.feature.reader.viewer.ReaderProgressIndicator
import ephyra.i18n.MR
import ephyra.presentation.core.data.coil.TachiyomiImageDecoder
import ephyra.presentation.core.ui.activity.BaseActivity
import ephyra.presentation.core.util.Navigator
import ephyra.presentation.core.util.ifSourcesLoaded
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.system.isNightMode
import ephyra.presentation.core.util.system.openInBrowser
import ephyra.presentation.core.util.system.toShareIntent
import ephyra.presentation.core.util.system.toast
import ephyra.presentation.core.util.view.applyHighRefreshRate
import ephyra.presentation.core.util.view.overrideTransitionCompat
import ephyra.presentation.core.util.view.setComposeContent
import ephyra.presentation.reader.DisplayRefreshHost
import ephyra.presentation.reader.OrientationSelectDialog
import ephyra.presentation.reader.ReaderContentOverlay
import ephyra.presentation.reader.ReaderPageActionsDialog
import ephyra.presentation.reader.ReaderPageIndicator
import ephyra.presentation.reader.ReadingModeSelectDialog
import ephyra.presentation.reader.appbars.ReaderAppBars
import ephyra.presentation.reader.settings.ReaderSettingsDialog
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.ByteArrayOutputStream

@OptIn(FlowPreview::class)
class ReaderActivity : BaseActivity() {

    companion object {
        fun newIntent(context: Context, mangaId: Long?, chapterId: Long?): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    private val readerPreferences: ReaderPreferences by inject()
    private val preferences: BasePreferences by inject()
    private val navigator: Navigator by inject()
    private val notificationManager: NotificationManager by inject()

    lateinit var binding: ReaderActivityBinding

    val viewModel by viewModel<ReaderViewModel>()
    private var assistUrl: String? = null

    /**
     * Configuration at reader level, like background color or forced orientation.
     */
    private var config: ReaderConfig? = null

    private var menuToggleToast: Toast? = null
    private var readingModeToast: Toast? = null
    private val displayRefreshHost: DisplayRefreshHost by lazy { DisplayRefreshHost(readerPreferences) }

    private val windowInsetsController: WindowInsetsControllerCompat by lazy {
        WindowInsetsControllerCompat(
            window,
            window.decorView,
        )
    }

    private var loadingIndicator: ReaderProgressIndicator? = null

    internal var isScrollingThroughPages = false

    override fun onCreate(savedInstanceState: Bundle?) {
        window.applyHighRefreshRate()

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        super.onCreate(savedInstanceState)

        binding = ReaderActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.setComposeOverlay()

        if (viewModel.needsInit()) {
            val manga = intent.extras?.getLong("manga", -1) ?: -1L
            val chapter = intent.extras?.getLong("chapter", -1) ?: -1L
            if (manga == -1L || chapter == -1L) {
                finish()
                return
            }
            notificationManager.dismissNewChaptersNotification(manga)

            lifecycleScope.launchNonCancellable {
                val initResult = viewModel.init(manga, chapter)
                if (!initResult.getOrDefault(false)) {
                    val exception = initResult.exceptionOrNull() ?: IllegalStateException("Unknown error")
                    withUIContext {
                        setInitialChapterError(exception)
                    }
                }
            }
        }

        config = ReaderConfig()

        viewModel.state
            .map { it.viewer }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { updateViewer() }
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    is ReaderViewModel.Event.ReloadViewerChapters -> updateViewer()
                    is ReaderViewModel.Event.PageChanged -> { /* TODO */ }
                    is ReaderViewModel.Event.SetOrientation -> setOrientation(event.orientation)
                    is ReaderViewModel.Event.SetCoverResult -> onSetAsCoverResult(event.result)
                    is ReaderViewModel.Event.BlockPageResult -> onBlockPageResult(event.result)
                    is ReaderViewModel.Event.SavedImage -> onSaveImageResult(event.result)
                    is ReaderViewModel.Event.ShareImage -> onShareImageResult(event.uri, event.page)
                    is ReaderViewModel.Event.CopyImage -> onCopyImageResult(event.uri)
                }
            }
            .launchIn(lifecycleScope)

        readerPreferences.showPageNumber().changes()
            .onEach { binding.setComposeOverlay() }
            .launchIn(lifecycleScope)

        readerPreferences.trueColor().changes()
            .onEach { updateViewer() }
            .launchIn(lifecycleScope)

        if (savedInstanceState != null) {
            menuToggleToast?.cancel()
        }
    }

    private fun ReaderActivityBinding.setComposeOverlay() {
        composeOverlay.setComposeContent {
            val state by viewModel.state.collectAsStateWithLifecycle()

            ContentOverlay(state)

            AppBars(state)

            if (state.currentPage == -1 && state.currentChapter != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            val currentChapter = state.currentChapter
            if (currentChapter != null) {
                val showPageNumber: Boolean by readerPreferences.showPageNumber().collectAsState()
                if (showPageNumber) {
                    ReaderPageIndicator(
                        currentPage = state.currentPage,
                        totalPages = state.totalPages,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                    )
                }
            }

            val dialog = state.dialog
            if (dialog != null) {
                when (dialog) {
                    is ReaderViewModel.Dialog.Loading -> { /* Handled by successState logic maybe? */ }
                    is ReaderViewModel.Dialog.Settings -> {
                        ReaderSettingsDialog(
                            onDismissRequest = viewModel::closeDialog,
                            onShowMenus = { viewModel.showMenus(true) },
                            onHideMenus = { viewModel.showMenus(false) },
                            screenModel = ReaderSettingsScreenModel(
                                readerState = viewModel.state,
                                onChangeReadingMode = viewModel::setMangaReadingMode,
                                onChangeOrientation = viewModel::setMangaOrientationType,
                                preferences = readerPreferences,
                            ),
                        )
                    }
                    is ReaderViewModel.Dialog.ReadingModeSelect -> {
                        ReadingModeSelectDialog(
                            onDismissRequest = viewModel::closeDialog,
                            screenModel = ReaderSettingsScreenModel(
                                readerState = viewModel.state,
                                onChangeReadingMode = viewModel::setMangaReadingMode,
                                onChangeOrientation = viewModel::setMangaOrientationType,
                                preferences = readerPreferences,
                            ),
                            onChange = { showToast(it) },
                        )
                    }
                    is ReaderViewModel.Dialog.OrientationModeSelect -> {
                        OrientationSelectDialog(
                            onDismissRequest = viewModel::closeDialog,
                            screenModel = ReaderSettingsScreenModel(
                                readerState = viewModel.state,
                                onChangeReadingMode = viewModel::setMangaReadingMode,
                                onChangeOrientation = viewModel::setMangaOrientationType,
                                preferences = readerPreferences,
                            ),
                            onChange = { showToast(it) },
                        )
                    }
                    is ReaderViewModel.Dialog.PageActions -> {
                        ReaderPageActionsDialog(
                            onDismissRequest = viewModel::closeDialog,
                            onSetAsCover = viewModel::setAsCover,
                            onShare = { viewModel.shareImage(it) },
                            onSave = viewModel::saveImage,
                            onBlockPage = viewModel::blockPage,
                            onUnblockPage = viewModel::unblockPage,
                            findMatchingBlockedHash = viewModel::findMatchingBlockedHash,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        config = null
        menuToggleToast?.cancel()
        readingModeToast?.cancel()
    }

    override fun onPause() {
        viewModel.onActivityFinish()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val menuVisible = viewModel.state.value.menuVisible
            setMenuVisibility(menuVisible)
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    override fun finish() {
        viewModel.onActivityFinish()
        super.finish()
        overrideTransitionCompat(
            R.anim.shared_axis_x_pop_enter,
            R.anim.shared_axis_x_pop_exit,
        )
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        val handled = viewModel.state.value.viewer?.handleKeyUp(keyCode, event) ?: false
        return handled || super.onKeyUp(keyCode, event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = viewModel.state.value.viewer?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = viewModel.state.value.viewer?.handleGenericMotionEvent(event) ?: false
        return handled || super.dispatchGenericMotionEvent(event)
    }

    @Composable
    private fun ContentOverlay(state: ReaderViewModel.State) {
        val flashOnPageChange: Boolean by readerPreferences.flashOnPageChange().collectAsState()

        val colorOverlayEnabled: Boolean by readerPreferences.colorFilter().collectAsState()
        val colorOverlay: Int by readerPreferences.colorFilterValue().collectAsState()
        val colorOverlayMode: Int by readerPreferences.colorFilterMode().collectAsState()
        val colorOverlayBlendMode = remember(colorOverlayMode) {
            when (colorOverlayMode) {
                1 -> BlendMode.Modulate
                2 -> BlendMode.Screen
                3 -> BlendMode.Overlay
                4 -> BlendMode.Lighten
                5 -> BlendMode.Darken
                else -> BlendMode.SrcOver
            }
        }

        ReaderContentOverlay(
            brightness = state.brightnessOverlayValue,
            color = colorOverlay.takeIf { colorOverlayEnabled },
            colorBlendMode = colorOverlayBlendMode,
        )

        if (flashOnPageChange) {
            DisplayRefreshHost(hostState = displayRefreshHost)
        }
    }

    @Composable
    fun AppBars(state: ReaderViewModel.State) {
        if (!ifSourcesLoaded()) {
            return
        }

        val isHttpSource = viewModel.getSource() is HttpSource

        val cropBorderPaged: Boolean by readerPreferences.cropBorders().collectAsState()
        val cropBorderWebtoon: Boolean by readerPreferences.cropBordersWebtoon().collectAsState()
        val isPagerType = ReadingMode.isPagerType(viewModel.getMangaReadingMode())
        val cropEnabled = if (isPagerType) cropBorderPaged else cropBorderWebtoon

        ReaderAppBars(
            visible = state.menuVisible,

            mangaTitle = state.manga?.title,
            chapterTitle = state.currentChapter?.chapter?.name,
            navigateUp = onBackPressedDispatcher::onBackPressed,
            onClickTopAppBar = ::openMangaScreen,
            bookmarked = state.bookmarked,
            onToggleBookmarked = viewModel::toggleChapterBookmark,
            onOpenInWebView = ::openChapterInWebView.takeIf { isHttpSource },
            onOpenInBrowser = ::openChapterInBrowser.takeIf { isHttpSource },
            onShare = ::shareChapter.takeIf { isHttpSource },

            viewer = state.viewer,
            onNextChapter = { lifecycleScope.launch { viewModel.loadNextChapter() } },
            enabledNext = state.viewerChapters?.nextChapter != null,
            onPreviousChapter = { lifecycleScope.launch { viewModel.loadPreviousChapter() } },
            enabledPrevious = state.viewerChapters?.prevChapter != null,
            currentPage = state.currentPage,
            totalPages = state.totalPages,
            onPageIndexChange = { viewModel.state.value.viewer?.moveToPage(it) },

            readingMode = ReadingMode.fromPreference(viewModel.getMangaReadingMode()),
            onClickReadingMode = { viewModel.openReadingModeSelectDialog() },
            orientation = ReaderOrientation.fromPreference(viewModel.getMangaOrientation()),
            onClickOrientation = { viewModel.openOrientationModeSelectDialog() },
            cropEnabled = cropEnabled,
            onClickCropBorder = { viewModel.toggleCropBorders() },
            onClickSettings = { viewModel.openSettingsDialog() },
        )
    }

    private fun setMenuVisibility(visible: Boolean) {
        viewModel.showMenus(visible)
        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun updateViewer() {
        val viewer = viewModel.state.value.viewer ?: return
        val view = viewer.getView()
        binding.viewerContainer.removeAllViews()
        binding.viewerContainer.addView(view)
        updateViewerInset(true, true)
    }

    private fun openMangaScreen() {
        val mangaId = viewModel.state.value.manga?.id ?: return
        navigator.openMangaScreen(this, mangaId)
    }

    private fun openChapterInWebView() {
        val url = viewModel.getChapterUrl() ?: return
        val manga = viewModel.state.value.manga ?: return
        navigator.openWebView(this, url, manga.source, manga.title)
    }

    private fun openChapterInBrowser() {
        val url = viewModel.getChapterUrl() ?: return
        openInBrowser(url)
    }

    private fun shareChapter() {
        val url = viewModel.getChapterUrl() ?: return
        startActivity(toShareIntent(url))
    }

    private fun showToast(stringRes: StringResource) {
        readingModeToast?.cancel()
        readingModeToast = toast(stringRes)
    }

    private fun setChapters(chapters: ViewerChapters) {
        viewModel.state.value.viewer?.setChapters(chapters)
    }

    private fun setInitialChapterError(error: Throwable) {
        logcat(LogPriority.ERROR, error) { "Error loading initial chapter" }
        AlertDialog.Builder(this)
            .setMessage(error.message)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun setProgressDialog(visible: Boolean) {
        if (visible) {
            if (loadingIndicator == null) {
                loadingIndicator = ReaderProgressIndicator(this).apply {
                    show()
                }
            }
        } else {
            loadingIndicator?.dismiss()
            loadingIndicator = null
        }
    }

    private fun moveToPageIndex(index: Int) {
        val viewer = viewModel.state.value.viewer ?: return
        viewer.moveToPage(index)
    }

    fun onPageSelected(page: ReaderPage) {
        viewModel.onPageSelected(page)
    }

    fun onPageLongTap(page: ReaderPage) {
        // TODO
    }

    fun requestPreloadChapter(chapter: ReaderChapter) {
        lifecycleScope.launchNonCancellable { viewModel.preload(chapter) }
    }

    fun toggleMenu() {
        setMenuVisibility(!viewModel.state.value.menuVisible)
    }

    fun showMenu() {
        setMenuVisibility(true)
    }

    fun hideMenu() {
        setMenuVisibility(false)
    }

    fun onShareImageResult(uri: Uri, page: ReaderPage) {
        startActivity(toShareIntent(uri))
    }

    fun onCopyImageResult(uri: Uri) {
        copyToClipboard(uri.toString(), uri.toString())
    }

    fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> toast(MR.strings.picture_saved)
            is ReaderViewModel.SaveImageResult.Error -> toast(result.error.message)
        }
    }

    fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        when (result) {
            Success -> toast(MR.strings.cover_updated)
            AddToLibraryFirst -> toast(MR.strings.notification_first_add_to_library)
            is Error -> toast(result.error.message)
        }
    }

    fun onBlockPageResult(result: ReaderViewModel.BlockPageResult) {
        // TODO
    }

    fun setOrientation(orientation: Int) {
        requestedOrientation = orientation
    }

    private fun updateViewerInset(all: Boolean, bottom: Boolean) {
        val viewer = viewModel.state.value.viewer ?: return
        val view = viewer.getView()
        view.applyInsetsPadding(windowInsetsController.lastWindowInsets, all, bottom)
    }

    private fun View.applyInsetsPadding(insets: WindowInsetsCompat?, all: Boolean, bottom: Boolean) {
        val systemBars = insets?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: androidx.core.graphics.Insets.NONE
        setPadding(
            if (all) systemBars.left else paddingLeft,
            if (all) systemBars.top else paddingTop,
            if (all) systemBars.right else paddingRight,
            if (all || bottom) systemBars.bottom else paddingBottom,
        )
    }

    inner class ReaderConfig {

        fun getCombinedPaint(isNightMode: Boolean, trueColor: Boolean): Paint {
            return Paint().apply {
                if (isNightMode && !trueColor) {
                    colorFilter = ColorMatrixColorFilter(
                        ColorMatrix().apply {
                            setSaturation(0f)
                            val matrix = floatArrayOf(
                                -1f, 0f, 0f, 0f, 255f,
                                0f, -1f, 0f, 0f, 255f,
                                0f, 0f, -1f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f,
                            )
                            postConcat(ColorMatrix(matrix))
                        },
                    )
                }
            }
        }

        val grayBackgroundColor = Color.GRAY

        init {
            readerPreferences.readerTheme().changes()
                .onEach { theme ->
                    val color = when (theme) {
                        0 -> Color.WHITE
                        1 -> Color.BLACK
                        2 -> automaticBackgroundColor()
                        else -> Color.GRAY
                    }
                    window.decorView.setBackgroundColor(color)
                    updateViewer()
                }
                .launchIn(lifecycleScope)

            readerPreferences.showPageNumber().changes()
                .onEach { binding.setComposeOverlay() }
                .launchIn(lifecycleScope)

            readerPreferences.trueColor().changes()
                .onEach { updateViewer() }
                .launchIn(lifecycleScope)

            readerPreferences.fullscreen().changes()
                .onEach { fullscreen ->
                    if (fullscreen) {
                        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                .launchIn(lifecycleScope)

            readerPreferences.keepScreenOn().changes()
                .onEach { setKeepScreenOn(it) }
                .launchIn(lifecycleScope)

            readerPreferences.customBrightness().changes()
                .onEach { setCustomBrightness(it) }
                .launchIn(lifecycleScope)

            readerPreferences.customBrightnessValue().changes()
                .onEach { setCustomBrightnessValue(it) }
                .launchIn(lifecycleScope)

            readerPreferences.colorFilter().changes()
                .onEach { updateViewer() }
                .launchIn(lifecycleScope)

            readerPreferences.colorFilterValue().changes()
                .onEach { updateViewer() }
                .launchIn(lifecycleScope)

            readerPreferences.colorFilterMode().changes()
                .onEach { updateViewer() }
                .launchIn(lifecycleScope)
        }

        private fun automaticBackgroundColor(): Int {
            return if (isNightMode()) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        }

        fun setDisplayProfile(data: String) {
            TachiyomiImageDecoder.displayProfile = data
            updateViewer()
        }

        fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        fun setCustomBrightness(enabled: Boolean) {
            if (enabled) {
                setCustomBrightnessValue(runBlocking { readerPreferences.customBrightnessValue().get() })
            } else {
                val layoutParams = window.attributes
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = layoutParams
            }
        }

        fun setCustomBrightnessValue(value: Int) {
            if (runBlocking { readerPreferences.customBrightness().get() }) {
                val layoutParams = window.attributes
                layoutParams.screenBrightness = value / 100f
                window.attributes = layoutParams
            }
        }

        fun setLayerPaint(isNightMode: Boolean, trueColor: Boolean) {
            val viewer = viewModel.state.value.viewer ?: return
            viewer.getView().setLayerType(LAYER_TYPE_HARDWARE, getCombinedPaint(isNightMode, trueColor))
        }
    }
}
