package com.jcapps.adbremote.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jcapps.adbremote.R
import com.jcapps.adbremote.ui.MainActivity

/**
 * Notification channel + ongoing notification of the ADB foreground service.
 *
 * In the artifact only the channel id string ("adb_notification_channel") and the singleton
 * INSTANCE survive (obf C0657); both helpers below were R8-inlined into [AdbService]
 * (leftover parameter checks "context", "msg", "btn"). RECON-UNSURE: helper names.
 */
// obf: C0657
object NotificationChannels {
    const val ADB_NOTIFICATION_CHANNEL = "adb_notification_channel"

    /** Creates the ADB service channel (API 26+). (inlined into AdbService.onCreate) */
    fun createAdbChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannelCompat.Builder(
                ADB_NOTIFICATION_CHANNEL,
                NotificationManagerCompat.IMPORTANCE_DEFAULT,
            )
                .setName(context.getString(R.string.device_connection))
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI, Notification.AUDIO_ATTRIBUTES_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }

    /** Builds the ongoing ADB service notification. (inlined into AdbService.onStartCommand) */
    fun createAdbNotification(context: Context, msg: String, btn: String): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, ServerService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val action = NotificationCompat.Action.Builder(R.drawable.ic_stop, btn, stopIntent).build()
        val builder = NotificationCompat.Builder(context, ADB_NOTIFICATION_CHANNEL)
            .setContentTitle(context.getString(R.string.adb_remote))
            .setContentText(msg)
            .setOngoing(true)
            .setSilent(true)
            .setSmallIcon(R.drawable.ic_notification)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(action)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED }
            ?: Intent(context, MainActivity::class.java).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
        builder.setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return builder.build()
    }
}
