package ephyra.feature.reader

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationCompat
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.notification.NotificationIds
import ephyra.core.common.util.system.cancelNotification
import ephyra.core.common.util.system.notificationBuilder
import ephyra.core.common.util.system.notify
import ephyra.i18n.MR
import ephyra.presentation.core.R
import ephyra.presentation.core.util.system.getBitmapOrNull
import ephyra.presentation.core.util.system.toShareIntent

/**
 * Class used to show BigPictureStyle notifications
 */
class SaveImageNotifier(private val context: Context) {

    private val notificationBuilder = context.notificationBuilder(NotificationIds.CHANNEL_COMMON)
    private val notificationId: Int = NotificationIds.ID_DOWNLOAD_IMAGE

    /**
     * Called when image download/copy is complete.
     *
     * @param uri image file containing downloaded page image.
     */
    fun onComplete(uri: Uri) {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .size(720, 1280)
            .target(
                onSuccess = { showCompleteNotification(uri, it.asDrawable(context.resources).getBitmapOrNull()) },
                onError = { onError(null) },
            )
            .build()
        context.imageLoader.enqueue(request)
    }

    /**
     * Clears the notification message.
     */
    fun onClear() {
        context.cancelNotification(notificationId)
    }

    /**
     * Called on error while downloading image.
     * @param error string containing error information.
     */
    fun onError(error: String?) {
        // Create notification
        with(notificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.download_notifier_title_error))
            setContentText(error ?: context.stringResource(MR.strings.unknown_error))
            setSmallIcon(android.R.drawable.ic_menu_report_image)
        }
        updateNotification()
    }

    private fun showCompleteNotification(uri: Uri, image: Bitmap?) {
        with(notificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.picture_saved))
            setSmallIcon(R.drawable.ic_photo_24dp)
            image?.let { setStyle(NotificationCompat.BigPictureStyle().bigPicture(it)) }
            setLargeIcon(image)
            setAutoCancel(true)

            // Clear old actions if they exist
            clearActions()

            setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/*")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            // Share action — direct PendingIntent avoids a broadcast roundtrip
            addAction(
                R.drawable.ic_share_24dp,
                context.stringResource(MR.strings.action_share),
                PendingIntent.getActivity(
                    context,
                    0,
                    uri.toShareIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )

            updateNotification()
        }
    }

    private fun updateNotification() {
        // Displays the progress bar on notification
        context.notify(notificationId, notificationBuilder.build())
    }
}
