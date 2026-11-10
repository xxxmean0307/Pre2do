package com.pastelle.todo.timing

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.pastelle.todo.PastelleApp
import com.pastelle.todo.R
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.data.db.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 计时前台服务（规格 2 计时行）：ongoing 通知显示当前状态 + mm:ss 倒计时，
 * 操作按钮「开始 / 跳过」；进程保活，退出计时后自动停止。
 * 倒计时由引擎每秒以墙钟推算，阶段结束由 AlarmManager 唤醒双保险。
 */
class TimingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engine get() = (application as PastelleApp).timingEngine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fallback = Notifications.ongoingNotification(
            this,
            getString(R.string.notif_ongoing_waiting),
            "",
            taskId = "",
            showStart = false,
            showSkip = false,
        )
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this,
                Notifications.ONGOING_ID,
                fallback,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(Notifications.ONGOING_ID, fallback)
        }
        scope.launch {
            engine.ui.collect { state -> render(state) }
        }
        return START_STICKY
    }

    private fun render(state: TimerUiState) {
        val active = state as? TimerUiState.Active
        if (active == null || active.phase !in listOf(TaskState.PREP, TaskState.FOCUS, TaskState.BREAK)) {
            stopForegroundCompat()
            stopSelf()
            return
        }
        val countdown = TimeUtils.formatMmSs(active.remainingMs)
        val title = when (active.phase) {
            TaskState.PREP -> getString(R.string.notif_ongoing_prep, active.task.title)
            TaskState.FOCUS -> getString(
                R.string.notif_ongoing_focus,
                active.pomodoroIndex,
                active.task.title,
            )
            else -> getString(R.string.notif_ongoing_break)
        }
        val notification = Notifications.ongoingNotification(
            this,
            title,
            countdown,
            taskId = active.task.id,
            showStart = active.phase == TaskState.PREP, // 「开始」= 提前开始
            showSkip = active.phase == TaskState.BREAK, // 「跳过」= 跳过休息
        )
        getSystemService(android.app.NotificationManager::class.java)
            .notify(Notifications.ONGOING_ID, notification)
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, TimingService::class.java))
            } catch (e: Exception) {
                // 后台启动受限时降级：引擎仍在进程内 tick，闹钟到点会再拉起
            }
        }
    }
}
