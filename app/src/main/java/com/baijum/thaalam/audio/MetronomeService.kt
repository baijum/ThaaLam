package com.baijum.thaalam.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.baijum.thaalam.MainActivity
import com.baijum.thaalam.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the metronome process alive while it plays in the background and offers
 * a transport notification. The audio itself lives in [MetronomeController]'s
 * engine; this service never owns the real-time thread.
 */
class MetronomeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var controller: MetronomeController? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val controller = MetronomeController.get(applicationContext)
        this.controller = controller
        startForeground(NOTIFICATION_ID, buildNotification("Preparing…"))
        observer = scope.launch {
            controller.playback
                .map { it.isPlaying to it.effectiveBpm }
                .distinctUntilChanged()
                .collect { (playing, bpm) ->
                    if (!playing) {
                        stopSelf()
                    } else {
                        val text = "${bpm.toInt()} BPM · ${controller.settings.value.meter.label}"
                        notificationManager().notify(NOTIFICATION_ID, buildNotification(text))
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller?.stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Metronome playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows the current tempo while ThaaLam plays in the background"
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MetronomeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ThaaLam metronome")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "thaalam_playback"
        private const val NOTIFICATION_ID = 1201
        const val ACTION_STOP = "com.baijum.thaalam.action.STOP"
    }
}
