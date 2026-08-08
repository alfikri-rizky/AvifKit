package com.alfikri.rizky.avifstudio.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Holds the process up while a batch runs.
 *
 * It does no work itself — the conversion stays in the ViewModel's coroutine. All this service does
 * is give the process a foreground priority the OS will not casually kill, and own the ongoing
 * notification that Android requires in exchange.
 */
class ConversionForegroundService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
    val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()
    val completed = intent?.getIntExtra(EXTRA_COMPLETED, 0) ?: 0
    val total = intent?.getIntExtra(EXTRA_TOTAL, 0) ?: 0
    val channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty()
    val channelDescription = intent?.getStringExtra(EXTRA_CHANNEL_DESC).orEmpty()

    if (intent?.action == ACTION_STOP) {
      stopForegroundCompat()
      stopSelf()
      return START_NOT_STICKY
    }

    // The channel text is localised, so it is resolved in composition and carried in rather than
    // looked up here — a Service has no access to Compose Resources.
    ensureChannel(this, channelName, channelDescription)
    val notification = buildNotification(this, title, body, completed, total)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    // Not sticky: if the OS does kill us, silently restarting with no batch to run would leave a
    // notification for work that no longer exists.
    return START_NOT_STICKY
  }

  private fun stopForegroundCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION") stopForeground(true)
    }
  }

  companion object {
    const val CHANNEL_ID = "conversions"
    const val NOTIFICATION_ID = 1001
    const val DONE_NOTIFICATION_ID = 1002

    private const val ACTION_STOP = "stop"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"
    private const val EXTRA_COMPLETED = "completed"
    private const val EXTRA_TOTAL = "total"
    private const val EXTRA_CHANNEL_NAME = "channelName"
    private const val EXTRA_CHANNEL_DESC = "channelDescription"

    fun start(
      context: Context,
      title: String,
      body: String,
      completed: Int,
      total: Int,
      channelName: String,
      channelDescription: String,
    ) {
      val intent =
        Intent(context, ConversionForegroundService::class.java)
          .putExtra(EXTRA_TITLE, title)
          .putExtra(EXTRA_BODY, body)
          .putExtra(EXTRA_COMPLETED, completed)
          .putExtra(EXTRA_TOTAL, total)
          .putExtra(EXTRA_CHANNEL_NAME, channelName)
          .putExtra(EXTRA_CHANNEL_DESC, channelDescription)
      // Foreground services may only be started while the app itself is in the foreground on
      // Android 12+, which is exactly when a batch begins.
      runCatching {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
          } else {
            context.startService(intent)
          }
        }
        .onFailure(ConversionSession::logStartFailure)
    }

    /**
     * Redraws the ongoing notification without touching the service. Denied POST_NOTIFICATIONS
     * makes this a silent no-op, which is the correct outcome — the conversion is unaffected.
     */
    fun updateProgress(
      context: Context,
      title: String,
      body: String,
      completed: Int,
      total: Int,
      channelName: String,
      channelDescription: String,
    ) {
      ensureChannel(context, channelName, channelDescription)
      runCatching {
        NotificationManagerCompat.from(context)
          .notify(NOTIFICATION_ID, buildNotification(context, title, body, completed, total))
      }
    }

    fun stop(context: Context) {
      runCatching {
        context.startService(
          Intent(context, ConversionForegroundService::class.java).setAction(ACTION_STOP)
        )
      }
    }

    fun ensureChannel(context: Context, name: String, description: String) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      // Unconditional: creating an existing channel id updates its name and description, which is
      // how the channel follows an in-app language change. Returning early froze it at whatever
      // locale happened to be active the first time a batch ran.
      manager.createNotificationChannel(
        // LOW: a progress bar that never makes a sound. This is ambient status, not an alert.
        NotificationChannel(
            CHANNEL_ID,
            name.ifBlank { "Conversions" },
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply { this.description = description }
      )
    }

    fun buildNotification(
      context: Context,
      title: String,
      body: String,
      completed: Int,
      total: Int,
    ): Notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(body)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(launchIntent(context))
        .apply { if (total > 0) setProgress(total, completed, false) }
        .build()

    fun notifyDone(
      context: Context,
      title: String,
      body: String,
      channelName: String,
      channelDescription: String,
    ) {
      ensureChannel(context, channelName, channelDescription)
      val notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
          .setContentTitle(title)
          .setContentText(body)
          .setSmallIcon(android.R.drawable.stat_sys_download_done)
          .setAutoCancel(true)
          .setContentIntent(launchIntent(context))
          .build()
      // Denied POST_NOTIFICATIONS makes this a no-op rather than a crash.
      runCatching {
        NotificationManagerCompat.from(context).notify(DONE_NOTIFICATION_ID, notification)
      }
    }

    /** Tapping either notification returns to the batch rather than starting somewhere odd. */
    private fun launchIntent(context: Context): PendingIntent? {
      val intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
      return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    }
  }
}
