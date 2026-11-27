package com.example.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object UpdateNotificationHelper {

    private const val CHANNEL_ALERTS = "kiko_updates_alerts"
    private const val CHANNEL_PROGRESS = "kiko_updates_progress"

    private const val NOTIF_ID_STATUS = 4101
    private const val NOTIF_ID_PROGRESS = 4102

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "KIKO Software Updates",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications for new KIKO AI updates, integrity verification, and installation status."
            }

            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS,
                "KIKO Update Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Live download progress for KIKO AI update packages."
                setSound(null, null)
            }

            manager.createNotificationChannel(alertChannel)
            manager.createNotificationChannel(progressChannel)
        }
    }

    fun notifyNewUpdateAvailable(context: Context, manifest: UpdateManifest, isMandatory: Boolean) {
        val prefix = if (isMandatory) "Critical Update Required" else "Optional Update"
        postAlert(
            context = context,
            title = UpdateStatusLabel.NEW_UPDATE_AVAILABLE.displayText,
            body = "$prefix: KIKO AI v${manifest.latestVersion} (${manifest.versionCode}) is available.",
        )
    }

    fun notifyDownloadingUpdate(context: Context, versionName: String, progressPercent: Int) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        val safePct = progressPercent.coerceIn(0, 100)
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(UpdateStatusLabel.DOWNLOADING_UPDATE.displayText)
            .setContentText("Downloading KIKO AI v$versionName • $safePct%")
            .setProgress(100, safePct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(createLaunchIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_PROGRESS, notification)
    }

    fun notifyDownloadCompleted(context: Context, versionName: String) {
        clearProgressNotification(context)
        postAlert(
            context = context,
            title = UpdateStatusLabel.DOWNLOAD_COMPLETED.displayText,
            body = "KIKO AI v$versionName package downloaded. Verifying SHA-256 checksum and signature…",
        )
    }

    fun notifyReadyToInstall(context: Context, versionName: String) {
        clearProgressNotification(context)
        postAlert(
            context = context,
            title = UpdateStatusLabel.READY_TO_INSTALL.displayText,
            body = "KIKO AI v$versionName passed SHA-256 and signature checks. Tap to open installer.",
        )
    }

    fun notifyInstallSucceeded(context: Context, versionName: String) {
        clearProgressNotification(context)
        postAlert(
            context = context,
            title = UpdateStatusLabel.SUCCESSFULLY_INSTALLED.displayText,
            body = "KIKO AI v$versionName is active and all systems are nominal.",
        )
    }

    fun notifyLatestVersion(context: Context, versionName: String) {
        postAlert(
            context = context,
            title = UpdateStatusLabel.LATEST_VERSION.displayText,
            body = "Installed KIKO AI v$versionName matches the latest release.",
        )
    }

    fun notifyUpdateFailed(context: Context, reason: String) {
        clearProgressNotification(context)
        postAlert(
            context = context,
            title = UpdateStatusLabel.FAILED_RETRY.displayText,
            body = reason.take(180),
        )
    }

    fun clearProgressNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_PROGRESS)
    }

    private fun postAlert(context: Context, title: String, body: String) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createLaunchIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_STATUS, notification)
    }

    private fun createLaunchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}
