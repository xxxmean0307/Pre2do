package com.pastelle.todo.timing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pastelle.todo.PastelleApp

/**
 * 开机恢复（规格 9.2）：BOOT_COMPLETED 重建所有未来闹钟；
 * 重启后任务时间窗已过的：错过 T0 不超过 30 分钟则直接进入 FOCUS，超过则交由界面标记「已错过」。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? PastelleApp ?: return
        val pending = goAsync()
        Thread {
            try {
                app.timingEngine.onBootRestore()
            } finally {
                pending.finish()
            }
        }.start()
    }
}
