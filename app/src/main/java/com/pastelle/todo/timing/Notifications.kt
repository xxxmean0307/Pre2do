package com.pastelle.todo.timing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pastelle.todo.MainActivity
import com.pastelle.todo.R

/**
 * 通知渠道与文案（规格 5.4，中文、温柔、简短）：
 * - pastelle_timer：前台服务常驻通知（当前状态 + mm:ss 倒计时，操作按钮 开始 / 跳过）
 * - pastelle_alerts：阶段提醒一次性通知
 */
object Notifications {
    const val CHANNEL_TIMER = "pastelle_timer"
    const val CHANNEL_ALERTS = "pastelle_alerts"

    const val ONGOING_ID = 1001

    /** 通知操作：开始 / 跳过（规格 2 通知行） */
    const val ACTION_START = "com.pastelle.todo.action.NOTIF_START"
    const val ACTION_SKIP = "com.pastelle.todo.action.NOTIF_SKIP"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TIMER,
                context.getString(R.string.channel_timer),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(context: Context, action: String, taskId: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).apply {
                this.action = action
                putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * 前台服务常驻通知：状态文案 + mm:ss 倒计时 + 操作按钮（开始 / 跳过）。
     * showStart / showSkip 决定当前阶段展示哪个操作。
     */
    fun ongoingNotification(
        context: Context,
        statusText: String,
        countdownText: String,
        taskId: String,
        showStart: Boolean,
        showSkip: Boolean,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(statusText)
            .setContentText(countdownText)
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (showStart) {
            builder.addAction(
                0,
                context.getString(R.string.notif_action_start),
                actionIntent(context, ACTION_START, taskId, 11),
            )
        }
        if (showSkip) {
            builder.addAction(
                0,
                context.getString(R.string.notif_action_skip),
                actionIntent(context, ACTION_SKIP, taskId, 12),
            )
        }
        return builder.build()
    }

    /** 一次性阶段提醒（规格 5.4 文案） */
    fun alert(context: Context, title: String, text: String, withStartAction: Boolean, taskId: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (withStartAction) {
            builder.addAction(
                0,
                context.getString(R.string.notif_action_start),
                actionIntent(context, ACTION_START, taskId, 13),
            )
        }
        context.getSystemService(NotificationManager::class.java)
            .notify((taskId.hashCode() and 0x7FFFFFFF) % 10000 + 2000, builder.build())
    }
}
