package com.pastelle.todo.ui.importflow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pastelle.todo.R
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.ui.components.BlackPillButton
import com.pastelle.todo.ui.components.ErrorListDialog
import com.pastelle.todo.ui.components.PastelBackground
import com.pastelle.todo.ui.components.PastelleCard
import com.pastelle.todo.ui.components.PillTrailingIcon
import com.pastelle.todo.ui.components.TaskTimelineCard
import com.pastelle.todo.ui.theme.Amber
import com.pastelle.todo.ui.theme.BlackPill
import com.pastelle.todo.ui.theme.Green
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.Motion
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.mainColor
import kotlinx.coroutines.delay

/**
 * 导入预览页（规格 6.2 第 2–3 步）：
 * 顶部日程名与日期、任务卡片时间线、软冲突/覆盖琥珀色提示、底部「确认导入 ✓」。
 * 确认 → 写入 Room → 胶囊变绿打勾 + spring 弹跳 → 1.2 秒后自动跳转今日页。
 */
@Composable
fun ImportPreviewScreen(
    vm: ImportViewModel,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val state by vm.state.collectAsState()
    var showOverwriteDialog by remember { mutableStateOf(false) }
    val preview = state as? ImportUiState.Preview
    val saved = preview?.saved == true

    // 确认后 1.2 秒自动跳转今日页
    LaunchedEffect(saved) {
        if (saved) {
            delay(1200)
            onImported()
        }
    }

    // 黑色胶囊 → 绿色打勾 + spring 弹跳
    val buttonColor by animateColorAsState(
        targetValue = if (saved) Green else BlackPill,
        animationSpec = tween(Motion.TweenMedMs),
        label = "import-button-color",
    )
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(saved) {
        if (saved) {
            bounce.snapTo(0.9f)
            bounce.animateTo(1f, Motion.SpringBouncy)
        }
    }

    val errorState = state as? ImportUiState.Errors
    if (errorState != null) {
        ErrorListDialog(
            errors = errorState.errors,
            onDismiss = onBack,
        )
    }

    PastelBackground(flowerSeed = 71) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.import_back),
                            tint = Ink,
                        )
                    }
                    Text(
                        stringResource(R.string.import_preview_title),
                        style = PastelleType.PageTitle,
                        color = Ink,
                    )
                }
                Spacer(Modifier.height(8.dp))

                when (val s = state) {
                    is ImportUiState.Preview -> PreviewContent(s)
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.import_parsing),
                            style = PastelleType.Body,
                            color = InkSoft,
                        )
                    }
                }
            }

            if (preview != null) {
                BlackPillButton(
                    label = when {
                        saved -> stringResource(R.string.import_done)
                        preview.saving -> stringResource(R.string.import_saving)
                        else -> stringResource(R.string.import_confirm)
                    },
                    onClick = {
                        if (!preview.saving && !preview.saved) {
                            if (preview.willOverwrite) {
                                showOverwriteDialog = true
                            } else {
                                vm.confirm()
                            }
                        }
                    },
                    enabled = !preview.saving,
                    trailingIcon = PillTrailingIcon.Check,
                    containerColor = buttonColor,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = bounce.value
                            scaleY = bounce.value
                        },
                )
            }
        }
    }

    if (showOverwriteDialog && preview != null) {
        Dialog(onDismissRequest = { showOverwriteDialog = false }) {
            PastelleCard {
                Text(
                    stringResource(R.string.import_overwrite_title),
                    style = PastelleType.CardTitle,
                    color = Ink,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.import_overwrite_message),
                    style = PastelleType.Body,
                    color = InkSoft,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showOverwriteDialog = false }) {
                        Text(
                            stringResource(R.string.action_cancel),
                            style = PastelleType.Body,
                            color = InkSoft,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    BlackPillButton(
                        label = stringResource(R.string.import_overwrite_confirm),
                        onClick = {
                            showOverwriteDialog = false
                            vm.confirm()
                        },
                        height = 48.dp,
                        trailingIcon = PillTrailingIcon.Check,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(state: ImportUiState.Preview) {
    val schedule = state.schedule
    val dateLabel = TimeUtils.parseDateOrNull(schedule.date)?.let {
        "${TimeUtils.monthDayLabel(it)}・${TimeUtils.weekdayLabel(it)}"
    } ?: schedule.date

    Column(Modifier.fillMaxSize()) {
        Text(schedule.title, style = PastelleType.CardTitle, color = Ink)
        Text(dateLabel, style = PastelleType.Caption, color = InkSoft)
        Spacer(Modifier.height(8.dp))

        // 琥珀色小字：覆盖提示 + 软冲突提示
        Column(Modifier.fillMaxWidth()) {
            if (state.willOverwrite) {
                Text(
                    stringResource(R.string.label_will_overwrite),
                    style = PastelleType.Caption,
                    color = Amber,
                )
            }
            state.warnings.forEach { warning ->
                Text(warning, style = PastelleType.Caption, color = Amber)
            }
        }
        if (state.willOverwrite || state.warnings.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            itemsIndexed(
                items = schedule.tasks,
                key = { _, task -> "${schedule.date}-${task.sourceIndex}" },
            ) { index, task ->
                TaskTimelineCard(
                    startLabel = TimeUtils.formatHhMm(task.startMinute),
                    title = task.title,
                    durationLabel = TimeUtils.minutesLabel(task.durationMin),
                    themeColor = task.color.mainColor(),
                    index = index,
                    stage = task.stage,
                    location = task.location,
                    note = task.note,
                )
            }
        }
    }
}
