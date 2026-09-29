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

/**
 * 计时前台服务
 *
 * 以后台前台服务的方式运行，持续显示通知栏通知以保持应用存活。
 * 每秒更新通知内容（显示当前正在计时的项目名称），
 * 并在跨天时自动检查并拆分跨天计时会话。
 */
class TimerService : Service() {

    companion object {
        // 通知渠道ID
        const val CHANNEL_ID = "timer_service_channel"
        // 前台通知ID
        const val NOTIFICATION_ID = 1

        /**
         * 启动计时服务（兼容 Android 8.0+ 前台服务要求）
         */
        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ 需使用 startForegroundService
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止计时服务
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }

    // 主线程Handler，用于定时轮询
    private val handler = Handler(Looper.getMainLooper())
    // IO协程作用域，用于执行数据库等耗时操作
    private val scope = CoroutineScope(Dispatchers.IO)
    // 记录上次检查跨天时的"一年中的第几天"，避免同一天重复检查
    private var lastCheckDay = -1

    /**
     * 定时任务Runnable，每秒执行一次
     * 职责：更新通知内容 + 检测跨天并拆分计时会话
     */
    private val updateRunnable = object : Runnable {
        override fun run() {
            // 更新前台通知内容
            updateNotification()

            // 获取今天是一年中的第几天，与上次记录值比较以判断是否跨天
            val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            if (today != lastCheckDay) {
                lastCheckDay = today
                // 跨天了，异步检查并拆分跨天会话
                scope.launch {
                    ProjectRepository.get(this@TimerService).checkAndSplitCrossDaySessions()
                }
            }

            // 1秒后再次执行
            handler.postDelayed(this, 1000)
        }
    }

    /**
     * 服务创建时调用：创建通知渠道、启动前台通知、初始化跨天检查、开始定时轮询
     */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("正在计时", "准备中..."))
        lastCheckDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        handler.post(updateRunnable)
    }

    /**
     * 服务启动命令处理，返回START_STICKY使服务被杀后自动重启
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    /**
     * 服务销毁时移除定时回调
     */
    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    /**
     * 绑定服务时返回null（本服务不支持绑定模式）
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 异步更新通知内容：查询正在计时的项目名称并刷新通知
     * 若无正在计时的项目则自动停止服务
     */
    private fun updateNotification() {
        scope.launch {
            val names = ProjectRepository.get(this@TimerService).getRunningProjectNames()
            if (names.isEmpty()) {
                // 没有正在计时的项目，停止自身
                stopSelf()
                return@launch
            }
            // 通知标题：单个项目显示名称，多个项目显示数量
            val title = if (names.size == 1) "正在计时: ${names[0]}" else "正在计时 ${names.size} 个项目"
            val text = names.joinToString("、")
            val notification = buildNotification(title, text)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * 构建前台通知对象
     *
     * @param title 通知标题
     * @param text  通知内容
     * @return 配置好的Notification对象
     */
    private fun buildNotification(title: String, text: String): Notification {
        // 点击通知跳转到主界面
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
            .setOngoing(true)    // 设置为常驻通知，不可滑动清除
            .setSilent(true)     // 静默通知，不发出声音
            .build()
    }

    /**
     * 创建通知渠道（Android 8.0+ 必需）
     * 渠道重要性设为LOW，避免发出声音打扰用户
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "计时服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持计时器在后台运行"
                setShowBadge(false)    // 不显示桌面角标
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
