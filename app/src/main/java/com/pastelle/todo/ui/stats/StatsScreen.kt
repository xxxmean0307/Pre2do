package com.pastelle.todo.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pastelle.todo.R
import com.pastelle.todo.core.PastelleConstants
import com.pastelle.todo.core.TaskColor
import com.pastelle.todo.data.db.DailyRecordEntity
import com.pastelle.todo.ui.components.MoodFace
import com.pastelle.todo.ui.components.MoodLevel
import com.pastelle.todo.ui.components.MonthSummaryCard
import com.pastelle.todo.ui.components.PastelBackground
import com.pastelle.todo.ui.components.PastelleCard
import com.pastelle.todo.ui.components.StatMiniCard
import com.pastelle.todo.ui.components.moodLevelFor
import com.pastelle.todo.ui.components.pastelleShadow
import com.pastelle.todo.ui.rememberRepository
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.Motion
import com.pastelle.todo.ui.theme.PastelleShapes
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.mainColor
import com.pastelle.todo.ui.theme.softColor
import androidx.compose.animation.core.animateFloatAsState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

private fun faceForGear(pct: Int?): MoodLevel = when (pct) {
    100 -> MoodLevel.Star
    60 -> MoodLevel.Smile
    else -> MoodLevel.Sleepy
}

/**
 * 统计页（规格 7.4）：
 * 今日 KPI 卡（今日番茄 / 专注分钟 / 准时率）→ 本周 7 根 pastel 渐变柱（spring 生长）→
 * Monthly Mood Summary 渐变卡 → 今日完成明细（偏差 + 表情 + XP）。
 */
@Composable
fun StatsScreen() {
    val repo = rememberRepository()
    val today = LocalDate.now()
    val todayStr = today.toString()
    val monthStart = today.withDayOfMonth(1).toString()
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth()).toString()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekDates = (0L..6L).map { weekStart.plusDays(it) }

    val todayTasks by produceState(initialValue = emptyList<com.pastelle.todo.data.db.TaskEntity>(), todayStr) {
        value = repo.tasksOfDate(todayStr)
    }
    val weekRecords by repo
        .observeDailyRange(weekStart.toString(), weekStart.plusDays(6).toString())
        .collectAsState(initial = emptyList())
    val monthRecords by repo.observeDailyRange(monthStart, monthEnd).collectAsState(initial = emptyList())
    val todayDone by repo.observeCompletedRange(todayStr, todayStr).collectAsState(initial = emptyList())

    // 今日 KPI：番茄 = Σ任务番茄数；专注 = 番茄 × 35min（规格 7.4）；准时率 = ±20% 窗口主动完成 / 总数
    val pomodoros = todayTasks.sumOf { it.pomodoroCount }
    val focusMinutes = pomodoros * (PastelleConstants.FOCUS_SECONDS / 60)
    val totalTasks = todayTasks.size
    val onTime = todayDone.count { (it.timeVariancePct ?: 0) in -20..19 }
    // ★ 准时率分母改为「已完成的任务」：以前把今天所有未完成任务算进分母，
    //   早晨刚排完日程必然接近 0%，数字完全没有参考意义。
    val onTimeRate = if (todayDone.isNotEmpty()) (onTime * 100f / todayDone.size) else 0f

    // 月度表情：Σ贡献 / Σ计划
    val planned = monthRecords.sumOf { it.plannedWeightMin }
    val earned = monthRecords.sumOf { it.earnedWeightMin }
    val hasCompletions = monthRecords.any { it.completedTasks > 0 }
    val avgPct: Float = if (planned > 0.0) (earned / planned * 100.0).toFloat() else 0f
    val monthLevel = moodLevelFor(avgPct, hasCompletions)
    val (monthWord, monthDesc) = when (monthLevel) {
        MoodLevel.Star -> stringResource(R.string.summary_word_great) to stringResource(R.string.summary_desc_great)
        MoodLevel.Smile -> stringResource(R.string.summary_word_good) to stringResource(R.string.summary_desc_good)
        MoodLevel.Sleepy -> stringResource(R.string.summary_word_try) to stringResource(R.string.summary_desc_try)
        MoodLevel.Neutral -> stringResource(R.string.summary_word_wait) to stringResource(R.string.summary_desc_wait)
    }

    PastelBackground(flowerSeed = 37) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 104.dp)   // 末项可滑出悬浮胶囊区
        ) {
            Text(stringResource(R.string.stats_title), style = PastelleType.PageTitle, color = Ink)
            Spacer(Modifier.height(16.dp))

            // KPI 三连
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatMiniCard(
                    caption = stringResource(R.string.stats_kpi_pomodoro),
                    value = "$pomodoros",
                    unit = stringResource(R.string.unit_pomodoros),
                    modifier = Modifier.weight(1f),
                )
                StatMiniCard(
                    caption = stringResource(R.string.stats_kpi_focus),
                    value = "$focusMinutes",
                    unit = stringResource(R.string.unit_minutes),
                    modifier = Modifier.weight(1f),
                )
                StatMiniCard(
                    caption = stringResource(R.string.stats_kpi_ontime),
                    value = "${onTimeRate.roundToInt()}%",
                    unit = "$onTime / $totalTasks",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))

            // 本周 7 根渐变柱
            val weekMap = weekRecords.associateBy { it.date }
            PastelleCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.stats_week_title), style = PastelleType.CardTitle, color = Ink)
                Spacer(Modifier.height(16.dp))
                val maxMin = (weekMap.values.maxOfOrNull { it.focusMinutes } ?: 0).coerceAtLeast(1)
                val dayLabels = stringArrayResource(R.array.week_days_short)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    weekDates.forEachIndexed { index, date ->
                        val minutes = weekMap[date.toString()]?.focusMinutes ?: 0
                        val theme = TaskColor.entries[index % TaskColor.entries.size]
                        val target = minutes.toFloat() / maxMin
                        val growth by animateFloatAsState(
                            targetValue = target,
                            animationSpec = Motion.SpringBouncy,
                            label = "week-bar-$index",
                        )
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height((110 * growth.coerceAtLeast(0.04f)).dp)
                                    .pastelleShadow()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(lerp(theme.softColor(), CardWhite, 0.25f), theme.softColor()),
                                        ),
                                        PastelleShapes.Pill,
                                    ),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(dayLabels[index], style = PastelleType.Caption, color = InkSoft)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Monthly Mood Summary
            MonthSummaryCard(
                title = stringResource(R.string.summary_title),
                word = monthWord,
                description = monthDesc,
                level = monthLevel,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            // 今日完成明细
            PastelleCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.stats_detail_title), style = PastelleType.CardTitle, color = Ink)
                Spacer(Modifier.height(12.dp))
                if (todayDone.isEmpty()) {
                    Text(
                        stringResource(R.string.stats_empty_detail),
                        style = PastelleType.Body,
                        color = InkSoft,
                    )
                } else {
                    todayDone.forEach { task ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MoodFace(level = faceForGear(task.completionPct), faceSize = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(task.title, style = PastelleType.Body, color = Ink)
                                val variance = task.timeVariancePct ?: 0
                                Text(
                                    stringResource(
                                        R.string.stats_detail_elapsed_fmt,
                                        task.elapsedMinOnComplete ?: 0,
                                        if (variance >= 0) "+" else "−",
                                        kotlin.math.abs(variance),
                                    ),
                                    style = PastelleType.Caption,
                                    color = InkSoft,
                                )
                            }
                            Text(
                                stringResource(R.string.stats_detail_xp_fmt, task.xpEarned ?: 0),
                                style = PastelleType.TabLabel,
                                color = Ink,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
