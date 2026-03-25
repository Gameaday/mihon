package ephyra.domain.track.interactor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import ephyra.app.R
import ephyra.app.data.notification.NotificationReceiver
import ephyra.app.data.notification.Notifications
import ephyra.app.ui.main.MainActivity
import ephyra.app.util.system.cancelNotification
import ephyra.app.util.system.notificationBuilder
import ephyra.app.util.system.notify
import ephyra.core.common.Constants
import ephyra.core.common.i18n.stringResource
import ephyra.i18n.MR

/**
 * Manages notifications for the authority matching job.
 *
 * Shows progress during matching ("Matching… 35% — Searching 'One Piece'…"),
 * and a completion notification with the result summary.
 */
class MatchUnlinkedNotifier(private val context: Context) {

    private val cancelIntent by lazy {
        NotificationReceiver.cancelMatchUnlinkedPendingBroadcast(context)
    }

    private val resultsPendingIntent by lazy {
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = Constants.SHORTCUT_MATCH_RESULTS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    /**
     * Reusable progress notification builder.
     * Public so [MatchUnlinkedJob.getForegroundInfo] can build the initial notification.
     */
    val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_MATCH_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.tracker_match_all_running))
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setLargeIcon(notificationBitmap)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                cancelIntent,
            )
        }
    }

    /**
     * Shows or updates the progress notification.
     *
     * @param mangaTitle the manga currently being processed (shown as subtitle).
     * @param current 1-based progress count.
     * @param total total manga to process.
     */
    fun showProgressNotification(mangaTitle: String, current: Int, total: Int) {
        progressNotificationBuilder
            .setContentTitle(
                context.stringResource(MR.strings.tracker_match_all_running_progress, current, total),
            )
            .setContentText(mangaTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mangaTitle))
            .setProgress(total, current, false)

        context.notify(
            Notifications.ID_MATCH_PROGRESS,
            progressNotificationBuilder.build(),
        )
    }

    /**
     * Shows a completion notification summarizing the matching results.
     *
     * @param result the [MatchUnlinkedManga.MatchResult] with linked/matched/total counts.
     */
    fun showCompleteNotification(result: MatchUnlinkedManga.MatchResult) {
        cancelProgressNotification()

        val totalResolved = result.linked + result.matched
        val unmatched = result.total - totalResolved

        val text = if (totalResolved > 0) {
            context.stringResource(
                MR.strings.tracker_match_all_result_detail,
                totalResolved,
                result.linked,
                result.matched,
                unmatched,
            )
        } else if (result.total == 0) {
            context.stringResource(MR.strings.tracker_match_all_none)
        } else {
            context.stringResource(
                MR.strings.tracker_match_all_no_matches_detail,
                result.total,
            )
        }

        context.notify(
            Notifications.ID_MATCH_COMPLETE,
            context.notificationBuilder(Notifications.CHANNEL_MATCH_PROGRESS) {
                setContentTitle(context.stringResource(MR.strings.tracker_match_all_complete_title))
                setContentText(text)
                setStyle(NotificationCompat.BigTextStyle().bigText(text))
                setSmallIcon(R.drawable.ic_ephyra)
                setLargeIcon(notificationBitmap)
                setAutoCancel(true)
                setContentIntent(resultsPendingIntent)
            }.build(),
        )
    }

    /**
     * Shows a failure notification when the job encounters an unexpected error.
     * Clicking opens the results screen so the user can retry individual items.
     */
    fun showFailureNotification() {
        cancelProgressNotification()

        context.notify(
            Notifications.ID_MATCH_COMPLETE,
            context.notificationBuilder(Notifications.CHANNEL_MATCH_PROGRESS) {
                setContentTitle(context.stringResource(MR.strings.tracker_match_all_complete_title))
                setContentText(context.stringResource(MR.strings.tracker_match_all_failed))
                setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.stringResource(MR.strings.tracker_match_all_failed)),
                )
                setSmallIcon(R.drawable.ic_ephyra)
                setLargeIcon(notificationBitmap)
                setAutoCancel(true)
                setContentIntent(resultsPendingIntent)
            }.build(),
        )
    }

    /**
     * Dismisses the progress notification.
     */
    fun cancelProgressNotification() {
        context.cancelNotification(Notifications.ID_MATCH_PROGRESS)
    }
}
