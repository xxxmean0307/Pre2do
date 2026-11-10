package com.pastelle.todo.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.pastelle.todo.MainActivity
import com.pastelle.todo.PastelleApp
import com.pastelle.todo.R
import com.pastelle.todo.core.HeroPhase
import com.pastelle.todo.core.TaskDisplayStatus
import com.pastelle.todo.core.TaskStatus
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.core.displayStatus
import com.pastelle.todo.core.hasPomodoro
import com.pastelle.todo.data.db.TaskEntity
import com.pastelle.todo.data.db.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 桌面小组件「今日速览」v8（M32 全量重写）。
 *
 * 与 App 主视觉对齐：淡紫流动渐变 + 28dp 液态玻璃外壳（镜面描边 / 冷青色散细线）、
 * 半透明玻璃主角卡、玻璃状态胶囊、淡紫→樱粉进度条、同心开环番茄钟；
 * 文字色值直接取 Color.kt 的 Ink / InkSoft / Purple / Pink。
 *
 * ★ 关键修复：以前只有 30 分钟周期刷新，「大倒计时」基本是冻结的。
 *   现在挂一个不唤醒设备的分钟级 RTC 闹钟，配合 App 内数据写入即时刷新。
 *   SCREEN_ON / USER_PRESENT 是 Android 8+ 静态注册收不到的隐式广播，已移除。
 */
class TodayWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK -> {
                // 分钟级刷新：BroadcastReceiver 生命周期短，用 goAsync 撑到渲染完成
                val pending = goAsync()
                refresh(context) { pending.finish() }
            }
            AppWidgetManager.ACTION_APPWIDGET_ENABLED,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> ensureMinuteTicker(context)
            AppWidgetManager.ACTION_APPWIDGET_DISABLED -> cancelMinuteTicker(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_TICK = "com.pastelle.todo.widget.TICK"
        private const val REQ_TICK = 907

        private val GLOWS = intArrayOf(
            R.drawable.widget_glass_glow0,
            R.drawable.widget_glass_glow1,
            R.drawable.widget_glass_glow2,
        )

        /** 数据变更后主动刷新（仓储写入点调用）；无组件时为空操作 */
        fun refresh(context: Context, onDone: (() -> Unit)? = null) {
            val app = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val am = AppWidgetManager.getInstance(app) ?: return@launch
                    val ids = am.getAppWidgetIds(ComponentName(app, TodayWidget::class.java))
                    if (ids.isEmpty()) return@launch
                    render(app, am, ids)
                } finally {
                    onDone?.invoke()
                }
            }
        }

        private fun tickIntent(context: Context): Intent =
            Intent(context, TodayWidget::class.java).setAction(ACTION_TICK)

        private fun tickPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQ_TICK,
                tickIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** RTC 不唤醒设备：亮屏后立即补发，倒计时每分钟走一格且几乎不耗电 */
        private fun ensureMinuteTicker(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val nextMinute = ((System.currentTimeMillis() / 60_000L) + 1) * 60_000L
            am.setRepeating(AlarmManager.RTC, nextMinute, 60_000L, tickPendingIntent(context))
        }

        private fun cancelMinuteTicker(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            am.cancel(tickPendingIntent(context))
        }

        private fun openIntent(context: Context, route: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route)
            }
            return PendingIntent.getActivity(
                context,
                route?.hashCode() ?: 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private suspend fun render(context: Context, am: AppWidgetManager, ids: IntArray) {
            val repo = (context.applicationContext as? PastelleApp)?.repository ?: return
            val today = TimeUtils.today()
            val tasks = repo.tasksOfDate(today).sortedBy { it.startMinute }
            val nowMs = System.currentTimeMillis()
            val done = tasks.count {
                it.state == TaskState.COMPLETED && it.completedAt != null
            }
            val total = tasks.size
            val dateLabel = TimeUtils.parseDateOrNull(today)
                ?.let { "${TimeUtils.monthDayLabel(it)}・${TimeUtils.weekdayLabel(it)}" } ?: today

            // 与今日页完全一致的 hero 选取口径
            val upcoming = tasks.filter { it.displayStatus(nowMs) != TaskDisplayStatus.COMPLETED }
            fun phaseOf(t: TaskEntity) = TaskStatus.heroCountdownFor(t, nowMs).phase
            val hero = upcoming.firstOrNull {
                phaseOf(it) == HeroPhase.Focus || phaseOf(it) == HeroPhase.Break
            } ?: upcoming.firstOrNull {
                phaseOf(it) == HeroPhase.Prep || phaseOf(it) == HeroPhase.Soon
            } ?: upcoming.getOrNull(0)
            val next = upcoming.filter { it !== hero }.getOrNull(0)

            for (id in ids) {
                val rv = RemoteViews(context.packageName, R.layout.widget_today_v8)

                // 色晕相位随分钟漂移：静态图也能有「流动背景」的呼吸感
                val glow = GLOWS[((nowMs / 60_000L) % GLOWS.size).toInt()]
                rv.setImageViewResource(R.id.widgetGlow, glow)
                rv.setTextViewText(R.id.widgetDate, dateLabel)
                rv.setTextViewText(
                    R.id.widgetCount,
                    context.getString(R.string.widget_count_fmt, done, total),
                )
                rv.setProgressBar(
                    R.id.widgetBar,
                    100,
                    if (total == 0) 0 else done * 100 / total,
                    false,
                )

                if (hero != null) {
                    rv.setViewVisibility(R.id.widgetHero, View.VISIBLE)
                    bindHero(context, rv, hero, nowMs)
                } else {
                    rv.setViewVisibility(R.id.widgetHero, View.GONE)
                }

                if (hero != null && next != null) {
                    rv.setViewVisibility(R.id.widgetNextRow, View.VISIBLE)
                    bindNext(context, rv, next, nowMs)
                } else {
                    rv.setViewVisibility(R.id.widgetNextRow, View.GONE)
                }

                rv.setTextViewText(
                    R.id.widgetEmpty,
                    context.getString(
                        if (total == 0) R.string.widget_empty_no_task
                        else R.string.widget_empty_all_done,
                    ),
                )
                rv.setViewVisibility(R.id.widgetEmpty, if (hero == null) View.VISIBLE else View.GONE)

                // 点按落点：正在进行 → 执行页；仅待开始 → 今日页；无任务 → 直接进编辑态
                val route = when {
                    hero == null -> MainActivity.ROUTE_SCHEDULE_EDIT
                    hero.state == TaskState.PREP || hero.state == TaskState.FOCUS ||
                        hero.state == TaskState.BREAK -> MainActivity.ROUTE_EXECUTION
                    else -> null
                }
                rv.setOnClickPendingIntent(R.id.widgetRoot, openIntent(context, route))
                am.updateAppWidget(id, rv)
            }
        }

        private fun bindHero(context: Context, rv: RemoteViews, task: TaskEntity, nowMs: Long) {
            val hc = TaskStatus.heroCountdownFor(task, nowMs)
            val display = task.displayStatus(nowMs)
            val color = themeColor(task.color)

            rv.setTextViewText(R.id.widgetHeroTitle, task.title)
            rv.setTextViewText(
                R.id.widgetHeroChip,
                context.getString(
                    when {
                        display == TaskDisplayStatus.COMPLETED -> R.string.widget_status_completed
                        hc.phase == HeroPhase.Break -> R.string.widget_status_break
                        hc.phase == HeroPhase.Focus -> R.string.widget_status_ongoing
                        hc.phase == HeroPhase.Prep -> R.string.widget_status_prep
                        hc.phase == HeroPhase.Soon -> R.string.widget_status_upcoming
                        else -> R.string.widget_status_missed
                    },
                ),
            )
            rv.setTextColor(R.id.widgetHeroChip, color)

            // 与 App 英雄卡同一个倒计时函数（短任务整段专注、长任务番茄/休息分段）
            rv.setTextViewText(
                R.id.widgetHeroBig,
                if (hc.phase == HeroPhase.Due) "00:00" else TimeUtils.formatMmSs(hc.remainingMs),
            )
            rv.setTextColor(R.id.widgetHeroBig, 0xFF332933.toInt())
            rv.setTextViewText(
                R.id.widgetHeroBigLabel,
                context.getString(
                    when {
                        display == TaskDisplayStatus.COMPLETED -> R.string.widget_label_done
                        hc.phase == HeroPhase.Break -> R.string.widget_label_break
                        hc.phase == HeroPhase.Soon || hc.phase == HeroPhase.Prep ->
                            R.string.widget_label_countdown
                        else -> R.string.widget_label_remaining
                    },
                ),
            )

            val hasPomo = task.hasPomodoro() &&
                (hc.phase == HeroPhase.Focus || hc.phase == HeroPhase.Break)
            rv.setViewVisibility(R.id.widgetPomoWrap, if (hasPomo) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.widgetHeroTime, if (hasPomo) View.GONE else View.VISIBLE)
            if (hasPomo) {
                val sec = (hc.remainingMs / 1000L).coerceAtLeast(0L)
                rv.setTextViewText(
                    R.id.widgetPomoCenter,
                    "%02d:%02d".format(sec / 60L % 60L, sec % 60L),
                )
            }
            rv.setTextViewText(R.id.widgetHeroTime, TimeUtils.minutesLabel(task.durationMin))
        }

        private fun bindNext(context: Context, rv: RemoteViews, task: TaskEntity, nowMs: Long) {
            rv.setImageViewResource(R.id.widgetNextDot, dotRes(task.color))
            rv.setTextViewText(R.id.widgetNextTime, TimeUtils.formatHhMm(task.startMinute))
            rv.setTextColor(R.id.widgetNextTime, themeColor(task.color))
            rv.setTextViewText(R.id.widgetNextLabel, task.title)
            val gapMin = ((TimeUtils.epochMs(task.date, task.startMinute) - nowMs) / 60_000L).toInt()
            rv.setTextViewText(
                R.id.widgetNextStatus,
                when (task.displayStatus(nowMs)) {
                    TaskDisplayStatus.COMPLETED -> context.getString(R.string.widget_status_completed)
                    TaskDisplayStatus.UPCOMING ->
                        context.getString(R.string.widget_gap_fmt, TimeUtils.humanGap(gapMin))
                    TaskDisplayStatus.PREP -> context.getString(R.string.widget_status_prep)
                    TaskDisplayStatus.ONGOING -> context.getString(R.string.widget_status_ongoing)
                    TaskDisplayStatus.MISSED -> context.getString(R.string.widget_status_missed)
                },
            )
        }

        /** 与 ui/theme/Color.kt 完全一致的 App 主色 */
        private fun themeColor(color: String): Int = when (color) {
            "purple" -> 0xFFB79CFF.toInt()
            "peach" -> 0xFFBC93D8.toInt()
            "blue" -> 0xFF8AB8FF.toInt()
            "green" -> 0xFF58CC02.toInt()
            else -> 0xFFFF8FB8.toInt()
        }

        private fun dotRes(color: String): Int = when (color) {
            "purple" -> R.drawable.widget_dot_purple
            "peach" -> R.drawable.widget_dot_peach
            "blue" -> R.drawable.widget_dot_blue
            "green" -> R.drawable.widget_dot_green
            else -> R.drawable.widget_dot_pink
        }
    }
}
