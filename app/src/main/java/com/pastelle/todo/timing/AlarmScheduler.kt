package com.pastelle.todo.timing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.data.db.TaskEntity

/**
 * AlarmManager 精确闹钟（规格 2 计时行 + 5.3.2）：
 * 为每个任务排 T0−5min（准备提醒）与 T0（开始专注）两个闹钟；
 * 计时中每个阶段结束时刻再排一个「唤醒校对」闹钟（与服务内墙钟核对双保险）。
 * 无精确闹钟权限时降级为不精确闹钟（规格 9.1：明确提示由「我的」页展示）。
 */
object AlarmScheduler {
    const val ACTION_PREP = "com.pastelle.todo.action.ALARM_PREP"
    const val ACTION_FOCUS = "com.pastelle.todo.action.ALARM_FOCUS"
    const val ACTION_WAKE = "com.pastelle.todo.action.ALARM_WAKE"

    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
    }

    /** 为一天的任务排 T0−5min 与 T0 闹钟（规格 6.2.3） */
    fun planTasks(context: Context, tasks: List<TaskEntity>, nowMs: Long = System.currentTimeMillis()) {
        tasks.forEach { task ->
            val t0 = TimeUtils.epochMs(task.date, task.startMinute)
            schedule(context, task.id, t0 - 300_000L, ACTION_PREP, nowMs)
            schedule(context, task.id, t0, ACTION_FOCUS, nowMs)
        }
    }

    /** 计时中：在当前阶段结束时刻排唤醒校对闹钟 */
    fun planPhaseWake(context: Context, taskId: String, atMs: Long) {
        schedule(context, taskId, atMs, ACTION_WAKE, System.currentTimeMillis())
    }

    fun cancelTask(context: Context, taskId: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context, taskId, ACTION_PREP, code(taskId, ACTION_PREP)))
        am.cancel(pending(context, taskId, ACTION_FOCUS, code(taskId, ACTION_FOCUS)))
        am.cancel(pending(context, taskId, ACTION_WAKE, code(taskId, ACTION_WAKE)))
    }

    private fun schedule(context: Context, taskId: String, atMs: Long, action: String, nowMs: Long) {
        if (atMs <= nowMs) return // 已过时刻由引擎补发，不排闹钟
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, taskId, action, code(taskId, action))
        try {
            if (canExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            }
        } catch (e: SecurityException) {
            // 权限被拒：降级为不精确提醒
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }

    private fun pending(context: Context, taskId: String, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).apply {
                this.action = action
                putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun code(taskId: String, action: String): Int = (taskId + action).hashCode() and 0x7FFFFFFF
}
