package com.pastelle.todo.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pastelle.todo.core.TaskColor
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.data.json.ImportCheckResult
import com.pastelle.todo.data.json.ScheduleJson
import com.pastelle.todo.data.json.ScheduleParser
import com.pastelle.todo.data.json.TaskJson
import com.pastelle.todo.ui.components.BlackPillButton
import com.pastelle.todo.ui.components.PastelBackground
import com.pastelle.todo.ui.components.PastelleCard
import com.pastelle.todo.ui.rememberRepository
import com.pastelle.todo.ui.rememberTimingEngine
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.mainColor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 任务草稿（全字符串编辑态，提交时组 JSON 走统一校验） */
private data class TaskDraft(
    val title: String = "",
    val start: String = "09:00",
    val durationMin: String = "60",
    val stage: String = "",
    val location: String = "",
    val note: String = "",
    val colorKey: String = "blue",
)

private val COLOR_OPTIONS = listOf(
    "blue" to "蓝", "pink" to "粉", "green" to "绿", "purple" to "紫", "peach" to "桃",
)

private val DURATION_CHIPS = listOf(15, 30, 45, 60, 90, 120, 180)

/**
 * 日程编辑器（App 内创建 / 编辑日程）。
 * 提交时组出与导入完全一致的 JSON，复用 ScheduleParser 的单测校验规则，
 * 再经 importSchedule 走覆盖式入库（与导入同一语义）。
 */
@Composable
fun ScheduleEditorScreen(
    editDate: String? = null,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val repository = rememberRepository()
    val engine = rememberTimingEngine()
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("我的一天") }
    var date by remember { mutableStateOf(editDate ?: TimeUtils.today()) }
    var tasks by remember { mutableStateOf(listOf(TaskDraft())) }
    var issues by remember { mutableStateOf<ImportCheckResult?>(null) }
    var overwriteWarn by remember { mutableStateOf(false) }

    // 编辑既有日程：回填当天任务
    LaunchedEffect(editDate) {
        if (editDate != null) {
            val existing = repository.tasksOfDate(editDate)
            if (existing.isNotEmpty()) {
                tasks = existing.sortedBy { it.startMinute }.map {
                    TaskDraft(
                        title = it.title,
                        start = "%02d:%02d".format(it.startMinute / 60, it.startMinute % 60),
                        durationMin = it.durationMin.toString(),
                        stage = it.stage ?: "",
                        location = it.location ?: "",
                        note = it.note ?: "",
                        colorKey = it.color,
                    )
                }
            }
            overwriteWarn = repository.hasSchedule(editDate)
        }
    }

    fun buildJson(): String {
        val schedule = ScheduleJson(
            version = 1,
            title = title.ifBlank { "我的一天" },
            date = date.trim(),
            tasks = tasks.mapIndexedNotNull { _, t ->
                if (t.title.isBlank()) null
                else TaskJson(
                    title = t.title,
                    start = t.start.trim(),
                    durationMin = t.durationMin.trim().toIntOrNull() ?: 0,
                    stage = t.stage.trim().ifBlank { null },
                    location = t.location.trim().ifBlank { null },
                    note = t.note.trim().ifBlank { null },
                    color = t.colorKey,
                )
            },
        )
        return Json.encodeToString(ScheduleJson.serializer(), schedule)
    }

    fun submit() {
        val result = ScheduleParser.parse(buildJson())
        issues = result
        val parsed = result.schedule
        if (parsed == null || result.errors.isNotEmpty()) return
        scope.launch {
            repository.importSchedule(parsed)
            engine.planImportedTasks(repository.tasksOfDate(parsed.date))
            onDone()
        }
    }

    PastelBackground(flowerSeed = 31) {
        Box(Modifier.fillMaxSize().imePadding()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 96.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "← 返回",
                        style = PastelleType.ButtonLabel,
                        color = Ink,
                        modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
                    )
                }
                Text(if (editDate == null) "新建日程" else "编辑日程", style = PastelleType.PageTitle, color = Ink)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 30) title = it },
                    label = { Text("日程标题（≤30 字）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("日期（YYYY-MM-DD）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (overwriteWarn) {
                    Spacer(Modifier.height(8.dp))
                    Text("该日期已有日程，保存将覆盖原有任务", style = PastelleType.Caption, color = Color(0xFFB87A2A))
                }

                Spacer(Modifier.height(16.dp))
                Text("任务列表", style = PastelleType.CardTitle, color = Ink)
                Spacer(Modifier.height(8.dp))

                tasks.forEachIndexed { i, draft ->
                    TaskCardEditor(
                        index = i,
                        draft = draft,
                        onDraftChange = { d -> tasks = tasks.toMutableList().also { l -> l[i] = d } },
                        onRemove = { tasks = tasks.filterIndexed { j, _ -> j != i } },
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "+ 添加任务",
                        style = PastelleType.ButtonLabel,
                        color = Ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x14000000))
                            .clickable { tasks = tasks + TaskDraft(start = nextStart(tasks)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }

                // 校验结果（复用导入规则；errors 阻断，warnings 仅提示）
                issues?.let { r ->
                    Spacer(Modifier.height(12.dp))
                    r.errors.forEach { e ->
                        Text("✕ $e", style = PastelleType.Caption, color = Color(0xFFC74B50))
                    }
                    r.warnings.forEach { w ->
                        Text("△ $w", style = PastelleType.Caption, color = Color(0xFFB87A2A))
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            BlackPillButton(
                label = "保存日程",
                onClick = { submit() },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            )
        }
    }
}

/** 新任务的默认开始时间：接在上一条之后 */
private fun nextStart(tasks: List<TaskDraft>): String {
    val last = tasks.lastOrNull() ?: return "09:00"
    val parts = last.start.split(":")
    val startMin = ((parts.getOrNull(0)?.toIntOrNull() ?: 9) * 60) + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    val end = startMin + (last.durationMin.toIntOrNull() ?: 60)
    val capped = end.coerceAtMost(23 * 60 + 45)
    return "%02d:%02d".format(capped / 60, capped % 60)
}

@Composable
private fun TaskCardEditor(
    index: Int,
    draft: TaskDraft,
    onDraftChange: (TaskDraft) -> Unit,
    onRemove: () -> Unit,
) {
    PastelleCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("第 ${index + 1} 条", style = PastelleType.Caption, color = InkSoft)
            Spacer(Modifier.weight(1f))
            Text(
                "删除",
                style = PastelleType.Caption,
                color = Color(0xFFC74B50),
                modifier = Modifier.clickable(onClick = onRemove).padding(6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onDraftChange(draft.copy(title = it.take(40))) },
            label = { Text("任务标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.start,
                onValueChange = { onDraftChange(draft.copy(start = it.take(5))) },
                label = { Text("开始") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = draft.durationMin,
                onValueChange = { onDraftChange(draft.copy(durationMin = it.filter { c -> c.isDigit() }.take(3))) },
                label = { Text("时长(分)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DURATION_CHIPS.forEach { d ->
                val sel = draft.durationMin == d.toString()
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Ink else Color(0x14000000))
                        .clickable { onDraftChange(draft.copy(durationMin = d.toString())) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text("$d", style = PastelleType.Caption, color = if (sel) Color.White else Ink)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COLOR_OPTIONS.forEach { (key, label) ->
                val sel = draft.colorKey == key
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Ink else Color(0x14000000))
                        .clickable { onDraftChange(draft.copy(colorKey = key)) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(TaskColorSwatch(key)))
                    Spacer(Modifier.size(4.dp))
                    Text(label, style = PastelleType.Caption, color = if (sel) Color.White else Ink)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = draft.location,
            onValueChange = { onDraftChange(draft.copy(location = it.take(24))) },
            label = { Text("地点（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = draft.note,
            onValueChange = { onDraftChange(draft.copy(note = it.take(120))) },
            label = { Text("备注（可选）") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TaskColorSwatch(key: String): Color {
    val tc = TaskColor.fromKey(key) ?: TaskColor.Blue
    return tc.mainColor()
}
