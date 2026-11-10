package com.pastelle.todo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pastelle.todo.R
import com.pastelle.todo.core.Greeting
import com.pastelle.todo.core.HeroPhase
import com.pastelle.todo.core.PastelleConstants
import com.pastelle.todo.core.TaskColor
import com.pastelle.todo.core.TaskDisplayStatus
import com.pastelle.todo.core.TaskStatus
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.core.displayStatus
import com.pastelle.todo.data.db.TaskEntity
import com.pastelle.todo.data.db.TaskState
import com.pastelle.todo.ui.components.CircleIconButton
import com.pastelle.todo.ui.components.HeroTaskCard
import com.pastelle.todo.ui.components.HomePermissionCard
import com.pastelle.todo.ui.components.PastelBackground
import com.pastelle.todo.ui.components.StatMiniCard
import com.pastelle.todo.ui.components.TaskTimelineCard
import com.pastelle.todo.ui.components.TopProgressBar
import com.pastelle.todo.ui.components.moodLevelFor
import com.pastelle.todo.ui.rememberRepository
import com.pastelle.todo.ui.schedule.MoodCalendar
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.Pink
import com.pastelle.todo.ui.theme.mainColor
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 今日页（规格 4.1，含参考图一视觉增补）：
 * 欢迎行（新建 + 日历）、大问候语、当前任务英雄卡、接下来的时间线、
 * 情绪日历、权限引导、进度条与 KPI 三连。
 *
 * ★ M32：补上「安排今天」主入口 —— 以前空态只有一句插画文案，
 *   新用户必须自己发现双指内划或 11sp 小字才能创建第一项日程。
 */
@Composable
fun HomeScreen(
    onOpenSchedule: () -> Unit = {},
    onOpenTask: () -> Unit = {},
    onAddTask: () -> Unit = {},
) {
    val repository = rememberRepository()
    val today = remember { TimeUtils.today() }
    var viewMonth by remember { mutableStateOf(YearMonth.now()) }
    var calSelected by remember { mutableStateOf(today) }
    val tasks by repository.observeTasks(today).collectAsState(initial = emptyList())
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (isActive) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }

    val sorted = tasks.sortedBy { it.startMinute }
    // hero 选取（时长走完让位）：进行中 > 即将/准备 > 已到计划时长的滞留任务
    val openTasks = sorted.filter { it.state != TaskState.COMPLETED }
    fun heroPhaseOf(t: TaskEntity) = TaskStatus.heroCountdownFor(t, nowMs).phase
    val heroTask: TaskEntity? =
        openTasks.firstOrNull { heroPhaseOf(it) == HeroPhase.Focus || heroPhaseOf(it) == HeroPhase.Break }
            ?: openTasks.firstOrNull { heroPhaseOf(it) == HeroPhase.Prep || heroPhaseOf(it) == HeroPhase.Soon }
            ?: openTasks.firstOrNull()
    val restTasks = sorted.filter { it !== heroTask }

    val (earned, planned) = TaskStatus.todayProgress(sorted)
    val progress = if (planned > 0) (earned / planned).toFloat() else 0f
    val completedCount = sorted.count { it.state == TaskState.COMPLETED }

    val pomodoroTotal = sorted.sumOf { it.pomodoroCount }
    // 与统计页同一口径（番茄数 × 专注段分钟），不再散落硬编码 35
    val focusMinutes = pomodoroTotal * (PastelleConstants.FOCUS_SECONDS / 60)

    val dateLabel = TimeUtils.parseDateOrNull(today)?.let {
        "${TimeUtils.monthDayLabel(it)}・${TimeUtils.weekdayLabel(it)}"
    } ?: today
    val greeting = Greeting.pick(
        hour = LocalTime.now().hour,
        completedCount = completedCount,
        totalCount = sorted.size,
        seed = today.hashCode(),
    )

    PastelBackground(flowerSeed = 11) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 124.dp),   // 末卡可滑出悬浮胶囊区（内容仍从胶囊下穿过）
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.padding(start = 12.dp))
                Column {
                    Text(
                        stringResource(R.string.home_welcome_back),
                        style = PastelleType.Caption,
                        color = InkSoft,
                    )
                    Text(dateLabel, style = PastelleType.CardTitle, color = Ink)
                }
                Spacer(Modifier.weight(1f))
                // 主行动按钮：一步直达今天的日程编辑态
                CircleIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add_task),
                    backgroundColor = Pink,
                    onClick = onAddTask,
                )
                Spacer(Modifier.width(10.dp))
                CircleIconButton(
                    icon = Icons.Outlined.CalendarMonth,
                    contentDescription = stringResource(R.string.cd_open_week),
                    onClick = onOpenSchedule,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(greeting, style = PastelleType.Greeting, color = Ink)

            Spacer(Modifier.height(20.dp))
            HeroTaskCard(task = heroTask, nowMs = nowMs, modifier = Modifier.fillMaxWidth(), onOpen = onOpenTask)

            // 空态不再是死胡同：给出可点的、与液态玻璃同族的胶囊按钮
            if (heroTask == null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCCFFFFFF))
                        .border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(50))
                        .clickable(onClick = onAddTask),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Ink)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.home_add_task),
                        style = PastelleType.ButtonLabel,
                        color = Ink,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_empty_hint),
                    style = PastelleType.Caption,
                    color = InkSoft,
                )
            }

            if (restTasks.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.home_next_title),
                    style = PastelleType.CardTitle,
                    color = Ink,
                )
                Spacer(Modifier.height(10.dp))
                restTasks.forEachIndexed { index, task ->
                    val gapMin =
                        ((TimeUtils.epochMs(task.date, task.startMinute) - nowMs) / 60_000L).toInt()
                    TaskTimelineCard(
                        startLabel = TimeUtils.formatHhMm(task.startMinute),
                        title = task.title,
                        durationLabel = TimeUtils.minutesLabel(task.durationMin),
                        themeColor = (TaskColor.fromKey(task.color) ?: TaskColor.Pink).mainColor(),
                        index = index,
                        stage = task.stage,
                        location = task.location,
                        note = task.note,
                        // ★ 与日程页 / 小组件共用同一状态判定，不再各自推导
                        trailing = when (task.displayStatus(nowMs)) {
                            TaskDisplayStatus.COMPLETED -> stringResource(R.string.status_completed)
                            TaskDisplayStatus.UPCOMING -> "距开始还有 ${TimeUtils.humanGap(gapMin)}"
                            TaskDisplayStatus.PREP -> stringResource(R.string.status_prep)
                            TaskDisplayStatus.ONGOING -> stringResource(R.string.status_ongoing)
                            TaskDisplayStatus.MISSED -> stringResource(R.string.status_missed)
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            val records by repository.observeDailyRange(
                TimeUtils.formatDay(viewMonth.atDay(1)),
                TimeUtils.formatDay(viewMonth.atEndOfMonth()),
            ).collectAsState(initial = emptyList())
            val dayMoods = remember(records) {
                records
                    .filter { it.totalTasks > 0 }
                    .associate { record ->
                        val avg = if (record.plannedWeightMin > 0) {
                            (record.earnedWeightMin / record.plannedWeightMin * 100).toFloat()
                        } else {
                            0f
                        }
                        record.date to moodLevelFor(avg, record.completedTasks > 0)
                    }
            }
            MoodCalendar(
                viewMonth = viewMonth,
                selectedDate = calSelected,
                today = today,
                dayMoods = dayMoods,
                onPrevMonth = { viewMonth = viewMonth.minusMonths(1) },
                onNextMonth = { viewMonth = viewMonth.plusMonths(1) },
                onSelectDate = { calSelected = it },
            )

            Spacer(Modifier.height(16.dp))
            HomePermissionCard(Modifier.fillMaxWidth())

            Spacer(Modifier.height(20.dp))
            TopProgressBar(
                progress = progress,
                completedCount = completedCount,
                totalCount = sorted.size,
            )

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatMiniCard(
                    caption = stringResource(R.string.stat_focus),
                    value = "$focusMinutes",
                    unit = stringResource(R.string.unit_minutes),
                    modifier = Modifier.weight(1f),
                )
                StatMiniCard(
                    caption = stringResource(R.string.stat_pomodoro),
                    value = "$pomodoroTotal",
                    unit = stringResource(R.string.unit_pomodoros),
                    modifier = Modifier.weight(1f),
                )
                StatMiniCard(
                    caption = stringResource(R.string.stat_tasks),
                    value = "$completedCount/${sorted.size}",
                    unit = stringResource(R.string.unit_tasks),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
