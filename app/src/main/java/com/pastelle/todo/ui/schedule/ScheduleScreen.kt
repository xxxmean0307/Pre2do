package com.pastelle.todo.ui.schedule

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pastelle.todo.R
import com.pastelle.todo.core.TaskColor
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.data.db.TaskEntity
import com.pastelle.todo.data.db.TaskState
import com.pastelle.todo.ui.components.BlackPillButton
import com.pastelle.todo.ui.components.CircleIconButton
import com.pastelle.todo.ui.components.MonthSummaryCard
import com.pastelle.todo.ui.components.PastelBackground
import com.pastelle.todo.ui.components.PastelleCard
import com.pastelle.todo.ui.components.StatMiniCard
import com.pastelle.todo.ui.components.TaskTimelineCard
import com.pastelle.todo.ui.components.moodLevelFor
import com.pastelle.todo.ui.importflow.ImportEntrySheet
import com.pastelle.todo.ui.importflow.ImportViewModel
import com.pastelle.todo.ui.rememberRepository
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.mainColor
import java.time.YearMonth

/**
 * 日程页（规格 4.3 + 参考图一视觉增补）：
 * Mood Calendar 月视图（整格表情脸）、当月 KPI 小卡三连、月度总结渐变卡、当日任务列表、一键导入入口。
 * 右上角白色圆形日历按钮 = 回到今天。
 */
@Composable
fun ScheduleScreen(
    vm: ImportViewModel,
    onOpenPreview: () -> Unit,
    onOpenEditor: (String?) -> Unit = {},
) {
    val repository = rememberRepository()
    val today = remember { TimeUtils.today() }
    var viewMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(today) }
    var showSheet by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // 可见月的每日记录（表情脸 + 月度统计）
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

    val monthFocus = records.sumOf { it.focusMinutes }
    val monthPomodoro = records.sumOf { it.pomodoroCount }
    val monthDone = records.sumOf { it.completedTasks }
    val monthEarned = records.sumOf { it.earnedWeightMin }
    val monthPlanned = records.sumOf { it.plannedWeightMin }
    val monthAvg = if (monthPlanned > 0) (monthEarned / monthPlanned * 100).toFloat() else 0f
    val summaryLevel = moodLevelFor(monthAvg, monthDone > 0)
    val summaryWordRes = when {
        monthDone == 0 -> R.string.summary_word_wait
        monthAvg >= 80f -> R.string.summary_word_great
        monthAvg >= 45f -> R.string.summary_word_good
        else -> R.string.summary_word_try
    }
    val summaryDescRes = when (summaryWordRes) {
        R.string.summary_word_great -> R.string.summary_desc_great
        R.string.summary_word_good -> R.string.summary_desc_good
        R.string.summary_word_try -> R.string.summary_desc_try
        else -> R.string.summary_desc_wait
    }

    // 选中日的任务
    val dayTasks by repository.observeTasks(selectedDate)
        .collectAsState(initial = emptyList())

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val text = uri?.let { picked ->
            try {
                context.contentResolver.openInputStream(picked)
                    ?.use { it.readBytes().decodeToString() }
            } catch (e: Exception) {
                null
            }
        }
        vm.parse(text)
        onOpenPreview()
    }

    PastelBackground(flowerSeed = 23) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 240.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.schedule_title),
                        style = PastelleType.PageTitle,
                        color = Ink,
                    )
                    Spacer(Modifier.weight(1f))
                    // 右上角白色圆形日历按钮：回到今天（参考图一）
                    CircleIconButton(
                        icon = Icons.Outlined.CalendarMonth,
                        contentDescription = stringResource(R.string.calendar_today),
                        onClick = {
                            viewMonth = YearMonth.now()
                            selectedDate = today
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))

                MoodCalendar(
                    viewMonth = viewMonth,
                    selectedDate = selectedDate,
                    today = today,
                    dayMoods = dayMoods,
                    onPrevMonth = { viewMonth = viewMonth.minusMonths(1) },
                    onNextMonth = { viewMonth = viewMonth.plusMonths(1) },
                    onSelectDate = { selectedDate = it },
                )

                Spacer(Modifier.height(14.dp))
                // 当月 KPI 小卡三连（参考图一）
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatMiniCard(
                        caption = stringResource(R.string.stat_focus),
                        value = "$monthFocus",
                        unit = stringResource(R.string.unit_minutes),
                        modifier = Modifier.weight(1f),
                    )
                    StatMiniCard(
                        caption = stringResource(R.string.stat_pomodoro),
                        value = "$monthPomodoro",
                        unit = stringResource(R.string.unit_pomodoros),
                        modifier = Modifier.weight(1f),
                    )
                    StatMiniCard(
                        caption = stringResource(R.string.stat_tasks),
                        value = "$monthDone",
                        unit = stringResource(R.string.unit_tasks),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(14.dp))
                // 月度总结渐变卡（参考图一 Monthly Mood Summary）
                MonthSummaryCard(
                    title = stringResource(R.string.summary_title),
                    word = stringResource(summaryWordRes),
                    description = stringResource(summaryDescRes),
                    level = summaryLevel,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selectedDate,
                        style = PastelleType.CardTitle,
                        color = Ink,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.schedule_day_count_fmt, dayTasks.size),
                        style = PastelleType.Caption,
                        color = InkSoft,
                    )
                    if (dayTasks.isNotEmpty()) {
                        Spacer(Modifier.padding(start = 10.dp))
                        BlackPillButton(
                            label = "编辑这天",
                            onClick = { onOpenEditor(selectedDate) },
                            modifier = Modifier,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (dayTasks.isEmpty()) {
                    PastelleCard(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.schedule_day_empty),
                            style = PastelleType.Body,
                            color = InkSoft,
                        )
                    }
                } else {
                    dayTasks.sortedBy { it.startMinute }.forEachIndexed { index, task ->
                        TaskTimelineCard(
                            startLabel = TimeUtils.formatHhMm(task.startMinute),
                            title = task.title,
                            durationLabel = TimeUtils.minutesLabel(task.durationMin),
                            themeColor = (TaskColor.fromKey(task.color) ?: TaskColor.Pink).mainColor(),
                            index = index,
                            stage = task.stage,
                            location = task.location,
                            note = task.note,
                            trailing = taskStatusLabel(task),
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .fillMaxWidth(),
            ) {
                BlackPillButton(
                    label = "新建日程",
                    onClick = { onOpenEditor(null) },
                    modifier = Modifier.weight(1f),
                )
                BlackPillButton(
                    label = stringResource(R.string.import_cta),
                    onClick = { showSheet = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showSheet) {
        ImportEntrySheet(
            onDismiss = { showSheet = false },
            onClipboard = {
                showSheet = false
                vm.parse(clipboard.getText()?.text)
                onOpenPreview()
            },
            onFile = {
                showSheet = false
                filePicker.launch(
                    arrayOf(
                        "application/json",
                        "text/plain",
                        "application/octet-stream",
                        "application/*",
                    )
                )
            },
        )
    }
}

/** 任务状态标签（里程碑 4 起由计时引擎细化） */
@Composable
private fun taskStatusLabel(task: TaskEntity): String = when {
    task.state == TaskState.COMPLETED -> stringResource(R.string.status_completed)
    else -> {
        val nowMs = System.currentTimeMillis()
        val t0 = TimeUtils.epochMs(task.date, task.startMinute)
        val prepStart = t0 - 300_000L
        val end = t0 + task.durationMin * 60_000L
        when {
            nowMs < prepStart -> stringResource(R.string.status_upcoming)
            nowMs < end -> stringResource(R.string.status_ongoing)
            else -> stringResource(R.string.status_missed)
        }
    }
}
