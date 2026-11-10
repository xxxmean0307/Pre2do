package com.pastelle.todo.timing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pastelle.todo.PastelleApp

/**
 * 闹钟与通知操作接收器：
 * - ALARM_PREP：T0−5min 准备提醒（规格 5.4 第 1 条）
 * - ALARM_FOCUS：T0 开始专注提醒（规格 5.4 第 2 条，带「开始」操作）
 * - ALARM_WAKE：阶段结束唤醒校对（与服务墙钟核对双保险）
 * - 通知按钮 ACTION_START / ACTION_SKIP：提前开始 / 跳过准备或休息
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PastelleApp ?: return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    AlarmScheduler.ACTION_PREP -> app.timingEngine.onPrepAlarm(taskId)
                    AlarmScheduler.ACTION_FOCUS -> app.timingEngine.onFocusAlarm(taskId)
                    AlarmScheduler.ACTION_WAKE -> app.timingEngine.onWakeAlarm(taskId)
                    Notifications.ACTION_START -> app.timingEngine.onNotifStart(taskId)
                    Notifications.ACTION_SKIP -> app.timingEngine.onNotifSkip(taskId)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
    }
}
