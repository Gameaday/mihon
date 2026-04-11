package ephyra.app.extension.util

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import ephyra.app.R
import ephyra.app.extension.installer.Installer
import ephyra.app.extension.installer.PackageInstallerInstaller
import ephyra.app.extension.installer.ShizukuInstaller
import ephyra.app.extension.util.ExtensionInstaller.Companion.EXTRA_DOWNLOAD_ID
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.notificationBuilder
import ephyra.data.notification.Notifications
import ephyra.domain.base.BasePreferences
import ephyra.i18n.MR
import ephyra.presentation.core.util.system.getSerializableExtraCompat
import logcat.LogPriority
import org.koin.android.ext.android.inject

class ExtensionInstallService : Service() {

    private val extensionManager: ephyra.app.extension.ExtensionManager by inject()
    private var installer: Installer? = null

    override fun onCreate() {
        val notification = notificationBuilder(Notifications.CHANNEL_EXTENSIONS_UPDATE) {
            setSmallIcon(R.drawable.ic_ephyra)
            setAutoCancel(false)
            setOngoing(true)
            setShowWhen(false)
            setContentTitle(stringResource(MR.strings.ext_install_service_notif))
            setProgress(100, 100, true)
        }.build()
        startForeground(Notifications.ID_EXTENSION_INSTALLER, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.data
        val id = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1)?.takeIf { it != -1L }
        val installerUsed = intent?.getSerializableExtraCompat<BasePreferences.ExtensionInstaller>(EXTRA_INSTALLER)
        if (uri == null || id == null || installerUsed == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (installer == null) {
            installer = when (installerUsed) {
                BasePreferences.ExtensionInstaller.PACKAGEINSTALLER -> PackageInstallerInstaller(this, extensionManager)
                BasePreferences.ExtensionInstaller.SHIZUKU -> ShizukuInstaller(this, extensionManager)
                else -> {
                    logcat(LogPriority.ERROR) { "Not implemented for installer $installerUsed" }
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        installer!!.addToQueue(id, uri)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        installer?.onDestroy()
        installer = null
    }

    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_INSTALLER = "EXTRA_INSTALLER"

        fun getIntent(
            context: Context,
            downloadId: Long,
            uri: Uri,
            installer: BasePreferences.ExtensionInstaller,
        ): Intent {
            return Intent(context, ExtensionInstallService::class.java)
                .setDataAndType(uri, ExtensionInstaller.APK_MIME)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                .putExtra(EXTRA_INSTALLER, installer)
        }
    }
}
