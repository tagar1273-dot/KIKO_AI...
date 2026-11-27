package com.example.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

data class ControlExecutionResult(
    val success: Boolean,
    val title: String,
    val message: String,
)

class AndroidControlManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "kiko_smart_alerts"
    }

    init {
        ensureNotificationChannel()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KIKO AI Tactical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Smart reminders, diagnostics alerts, and automation triggers from KIKO AI."
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun setTorch(enable: Boolean): ControlExecutionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return ControlExecutionResult(false, "Torch Unavailable", "CameraManager service not available.")
            val torchCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ControlExecutionResult(
                false,
                "No Flash Hardware",
                "This device or emulator does not report a hardware flashlight unit."
            )
            cameraManager.setTorchMode(torchCameraId, enable)
            ControlExecutionResult(
                true,
                if (enable) "Torch Activated" else "Torch Deactivated",
                if (enable) "Flashlight beam engaged via CameraManager." else "Flashlight beam powered down."
            )
        } catch (e: Exception) {
            ControlExecutionResult(false, "Torch Control Error", e.localizedMessage ?: "Failed to toggle flashlight.")
        }
    }

    fun adjustVolume(direction: Int): ControlExecutionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return ControlExecutionResult(false, "Audio Unavailable", "AudioManager not available.")
            val flag = AudioManager.FLAG_SHOW_UI
            when {
                direction > 0 -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, flag)
                direction < 0 -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, flag)
                else -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, flag)
            }
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val pct = (current * 100) / max
            ControlExecutionResult(true, "Media Volume Updated", "Media output level set to $pct%.")
        } catch (e: Exception) {
            ControlExecutionResult(false, "Audio Control Error", e.localizedMessage ?: "Could not adjust volume.")
        }
    }

    fun setAlarm(hour: Int, minute: Int, message: String): ControlExecutionResult {
        return launchActivityIntent(
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, message.ifBlank { "KIKO AI Alert" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            },
            successTitle = "Alarm Dispatched",
            successMsg = "Clock app opened to set alarm for %02d:%02d.".format(hour, minute)
        )
    }

    fun startTimer(seconds: Int, message: String): ControlExecutionResult {
        return launchActivityIntent(
            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(5))
                putExtra(AlarmClock.EXTRA_MESSAGE, message.ifBlank { "KIKO Timer" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            },
            successTitle = "Timer Dispatched",
            successMsg = "Started ${seconds}s countdown timer."
        )
    }

    fun openSettingsPanel(action: String, label: String): ControlExecutionResult {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (action == Settings.Panel.ACTION_WIFI ||
                action == Settings.Panel.ACTION_VOLUME ||
                action == Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        ) {
            Intent(action)
        } else {
            val fallback = when (action) {
                Settings.Panel.ACTION_WIFI, Settings.Panel.ACTION_INTERNET_CONNECTIVITY -> Settings.ACTION_WIFI_SETTINGS
                Settings.Panel.ACTION_VOLUME -> Settings.ACTION_SOUND_SETTINGS
                else -> action
            }
            Intent(fallback)
        }
        return launchActivityIntent(
            intent,
            successTitle = "$label Opened",
            successMsg = "Launched Android system $label panel."
        )
    }

    fun openWebUrl(url: String): ControlExecutionResult {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        return launchActivityIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse(normalized)),
            successTitle = "Browser Launched",
            successMsg = "Opened $normalized"
        )
    }

    fun shareText(title: String, body: String): ControlExecutionResult {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return launchActivityIntent(
            Intent.createChooser(shareIntent, "Share via KIKO AI"),
            successTitle = "Share Sheet Opened",
            successMsg = "Prepared content for external sharing."
        )
    }

    fun copyToClipboard(label: String, text: String): ControlExecutionResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ControlExecutionResult(false, "Clipboard Unavailable", "Could not access system clipboard.")
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        return ControlExecutionResult(true, "Copied to Clipboard", "$label copied cleanly.")
    }

    fun postSmartNotification(title: String, message: String, notificationId: Int = 1001): ControlExecutionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return ControlExecutionResult(
                    false,
                    "Notification Permission Needed",
                    "Grant notification permission in Systems to receive KIKO smart alerts."
                )
            }
        }
        return try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            ControlExecutionResult(true, "Smart Notification Sent", title)
        } catch (e: SecurityException) {
            ControlExecutionResult(false, "Notification Blocked", e.localizedMessage ?: "Permission denied.")
        }
    }

    private fun launchActivityIntent(
        intent: Intent,
        successTitle: String,
        successMsg: String,
    ): ControlExecutionResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ControlExecutionResult(true, successTitle, successMsg)
        } catch (e: Exception) {
            ControlExecutionResult(false, "Action Unavailable", e.localizedMessage ?: "No handler found on this device.")
        }
    }
}
