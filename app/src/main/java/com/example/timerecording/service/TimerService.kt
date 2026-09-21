package com.example.timerecording.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.timerecording.MainActivity
import com.example.timerecording.R
import com.example.timerecording.data.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_service_channel"
        const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastCheckDay = -1

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNotification()

            val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            if (today != lastCheckDay) {
                lastCheckDay = today
                scope.launch {
                    ProjectRepository.get(this@TimerService).checkAndSplitCrossDaySessions()
                }
            }

            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("正在计时", "准备中..."))
        lastCheckDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        handler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification() {
        scope.launch {
            val names = ProjectRepository.get(this@TimerService).getRunningProjectNames()
            if (names.isEmpty()) {
                stopSelf()
                return@launch
            }
            val title = if (names.size == 1) "正在计时: ${names[0]}" else "正在计时 ${names.size} 个项目"
            val text = names.joinToString("、")
            val notification = buildNotification(title, text)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "计时服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持计时器在后台运行"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
