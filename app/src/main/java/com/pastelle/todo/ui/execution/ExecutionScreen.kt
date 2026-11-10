package com.pastelle.todo.ui.execution

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pastelle.todo.PastelleApp
import com.pastelle.todo.R
import com.pastelle.todo.core.PastelleConstants
import com.pastelle.todo.core.TaskColor
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.data.db.TaskState
import com.pastelle.todo.timing.TimerUiState
import com.pastelle.todo.ui.components.BlackPillButton
import com.pastelle.todo.ui.components.CircleIconButton
import com.pastelle.todo.ui.components.PastelBackground
import com.pastelle.todo.ui.components.MoodFace
import com.pastelle.todo.ui.components.MoodLevel
import com.pastelle.todo.ui.components.PillTrailingIcon
import com.pastelle.todo.ui.rememberTimingEngine
import com.pastelle.todo.ui.theme.BgGradientMid
import com.pastelle.todo.ui.theme.BgGradientTop
import com.pastelle.todo.ui.theme.BlackPill
import com.pastelle.todo.ui.theme.BlueSoft
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.Dimens
import com.pastelle.todo.ui.theme.GreenSoft
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.PeachSoft
import com.pastelle.todo.ui.theme.PinkSoft
import com.pastelle.todo.ui.theme.ProgressFillEnd
import com.pastelle.todo.ui.theme.PurpleSoft
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin
import kotlinx.coroutines.launch

private val FOCUS_MS = PastelleConstants.FOCUS_SECONDS * 1000L
private val BREAK_MS = PastelleConstants.BREAK_SECONDS * 1000L

/**
 * 任务执行页（规格 4.2）：
 * PREP = 粉紫渐变 + 漂浮花朵 + 细圆环 +「提前开始」；
 * FOCUS = 白底粉渐变 + 大圆环渐变进度 +「第 N 个番茄钟」+ 完成按钮（80% 前置灰）；
 * BREAK = 绿渐变 + 表情脸放松 +「跳过休息」。
 * 全程墙钟倒计时；FOCUS 保持屏幕常亮；下滑或返回仅退出全屏 UI，计时不中断。
 */
@Composable
fun ExecutionScreen(
    onBack: () -> Unit,
    onCompleted: (com.pastelle.todo.timing.TimingEvent.Completed) -> Unit = {},
) {
    val engine = rememberTimingEngine()
    val state by engine.ui.collectAsState()
    val scope = rememberCoroutineScope()

    val active = state as? TimerUiState.Active
    val phase = active?.phase

    // FOCUS 保持屏幕常亮（规格 4.2）
    val view = LocalView.current
    DisposableEffect(phase) {
        view.keepScreenOn = phase == TaskState.FOCUS
        onDispose { view.keepScreenOn = false }
    }

    // 下滑退出全屏 UI（计时不中断）
    val dragAccum = remember { FloatArray(1) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccum[0] = 0f },
                    onVerticalDrag = { _, amount ->
                        dragAccum[0] += amount
                        if (dragAccum[0] > 160f) {
                            dragAccum[0] = 0f
                            onBack()
                        }
                    },
                )
            },
    ) {
        when {
            active == null || phase == null -> ExecEmptyState(onBack)
            phase == TaskState.FOCUS -> FocusLayout(active, onBack) { gear ->
                scope.launch {
                    // 先落库（engine.complete 内部），成功才播放奖励动画（规格 9.6）
                    val ev = engine.complete(active.task.id, gear)
                    if (ev != null) onCompleted(ev)
                }
            }
            phase == TaskState.BREAK -> BreakLayout(active, onBack) {
                scope.launch { engine.skipBreak(active.task.id) }
            }
            else -> PrepLayout(active, onBack) {
                scope.launch { engine.startEarly(active.task.id) }
            }
        }
    }
}

// ── 准备 / 即将开始 ───────────────────────────────────

@Composable
private fun PrepLayout(state: TimerUiState.Active, onBack: () -> Unit, onStartEarly: () -> Unit) {
    PhaseBackground()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExecTopBar(onBack)
        Spacer(Modifier.height(28.dp))
        Text(
            text = if (state.phase == TaskState.WAITING) stringResource(R.string.exec_label_soon)
            else stringResource(R.string.exec_label_prep),
            style = PastelleType.TabLabel,
            color = InkSoft,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = state.task.title,
            style = PastelleType.CardTitle,
            color = Ink,
        )
        Spacer(Modifier.height(36.dp))
        RingCountdown(
            progress = 1f,
            centerTime = TimeUtils.formatMmSs(state.remainingMs),
            ringColors = listOf(PinkSoft, PurpleSoft),
            ringWidth = Dimens.ExecutionRingThinWidth,
            accessibilityLabel = a11yCountdown(state.remainingMs),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(36.dp))
        Text(
            text = stringResource(R.string.exec_prep_hint),
            style = PastelleType.Body,
            color = InkSoft,
        )
        Spacer(Modifier.height(48.dp))
        BlackPillButton(
            label = stringResource(R.string.exec_start_early),
            onClick = onStartEarly,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = PillTrailingIcon.Arrow,
        )
    }
}

// ── 专注中 ───────────────────────────────────────────

@Composable
private fun FocusLayout(state: TimerUiState.Active, onBack: () -> Unit, onComplete: (Int) -> Unit) {
    PhaseBackground()
    val task = state.task
    val theme = TaskColor.fromKey(task.color) ?: TaskColor.Pink
    val ringColors = when (theme.key) {
        "purple" -> listOf(PurpleSoft, PinkSoft)
        "blue" -> listOf(BlueSoft, PurpleSoft)
        "green" -> listOf(GreenSoft, BlueSoft)
        "peach" -> listOf(PeachSoft, PinkSoft)
        else -> listOf(PinkSoft, PeachSoft)
    }
    val hasPomodoro = task.durationMin >= com.pastelle.todo.core.PastelleConstants.POMODORO_MIN_DURATION_MIN
    val progress = if (hasPomodoro) {
        1f - (state.remainingMs.toFloat() / FOCUS_MS).coerceIn(0f, 1f)
    } else {
        (state.elapsedMs.toFloat() / (task.durationMin * 60000f)).coerceIn(0f, 1f)
    }
    val gateMinutes = ceil(state.msToGate / 60000.0).toInt().coerceAtLeast(1)
    val gateLabel = stringResource(R.string.exec_gate_fmt, gateMinutes)
    val completeLabel = stringResource(R.string.exec_complete)

    // 延时（顺延开始时刻）
    val engine = rememberTimingEngine()
    val scope = rememberCoroutineScope()

    fun doDelay(m: Int) {
        scope.launch { engine.delayTask(state.task.id, m) }
    }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 150.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExecTopBar(onBack)
        if (hasPomodoro) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.exec_focus_pomodoro, state.pomodoroIndex),
                style = PastelleType.TabLabel,
                color = InkSoft,
            )
        }
        Spacer(Modifier.height(24.dp))
        RingCountdown(
            progress = progress,
            centerTime = TimeUtils.formatMmSs(state.remainingMs),
            ringColors = ringColors,
            waveColor = PinkSoft,
            accessibilityLabel = a11yCountdown(state.remainingMs),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = task.title,
            style = PastelleType.CardTitle,
            color = Ink,
        )
        if (!task.note.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = task.note,
                style = PastelleType.Body,
                color = InkSoft,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row {
            DelayChip(label = "+15 分") { doDelay(15) }
            Spacer(Modifier.padding(horizontal = 5.dp))
            DelayChip(label = "+30 分") { doDelay(30) }
        }
        Spacer(Modifier.height(40.dp))
    }
    // 完成滑块主机组件（固定底部，永不与滚动抢手势；按住升起竖向长椭圆滑块，规格 7.2）
    com.pastelle.todo.ui.reward.CompletionSliderHost(
        enabled = state.canComplete,
        forced = state.forced,
        gateLabel = gateLabel,
        onConfirm = { gear -> onComplete(gear) },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    )
    }
}

// ── 休息中 ───────────────────────────────────────────

@Composable
private fun BreakLayout(state: TimerUiState.Active, onBack: () -> Unit, onSkip: () -> Unit) {
    PhaseBackground()

    // 表情脸放松动画：缓慢呼吸张合 + 轻点头（规格 4.2）
    val breathePhase by rememberInfiniteTransition(label = "break-face").animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "break-face-phase",
    )
    val faceScale = 1f + 0.04f * sin(breathePhase)
    val faceNod = 3f * sin(breathePhase * 0.5f)
    val skipLabel = stringResource(R.string.exec_skip_break)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExecTopBar(onBack)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.exec_break_label),
            style = PastelleType.CardTitle,
            color = Ink,
        )
        Spacer(Modifier.height(24.dp))
        MoodFace(
            level = MoodLevel.Smile,
            faceSize = 72.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = faceScale
                scaleY = faceScale
                rotationZ = faceNod
            },
        )
        Spacer(Modifier.height(24.dp))
        RingCountdown(
            progress = 1f - (state.remainingMs.toFloat() / BREAK_MS).coerceIn(0f, 1f),
            centerTime = TimeUtils.formatMmSs(state.remainingMs),
            ringColors = listOf(GreenSoft, ProgressFillEnd),
            ringWidth = Dimens.ExecutionRingThinWidth,
            accessibilityLabel = a11yCountdown(state.remainingMs),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.exec_break_hint),
            style = PastelleType.Body,
            color = InkSoft,
        )
        Spacer(Modifier.height(40.dp))
        Text(
            text = skipLabel,
            style = PastelleType.ButtonLabel,
            color = BlackPill,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onSkip)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .semantics { contentDescription = skipLabel },
        )
    }
}

// ── 公共 ─────────────────────────────────────────────

/** 流动动态背景（与全局 PastelBackground 同族） */
@Composable
private fun PhaseBackground() {
    PastelBackground(
        modifier = Modifier.fillMaxSize(),
        content = {},
    )
}

@Composable
private fun ExecTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.exec_cd_back),
            onClick = onBack,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ExecEmptyState(onBack: () -> Unit) {
    PhaseBackground()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExecTopBar(onBack)
        Spacer(Modifier.height(120.dp))
        Text(
            text = stringResource(R.string.exec_empty),
            style = PastelleType.Body,
            color = InkSoft,
        )
        // ★ 空态不再是「点了没反应」的死路：给一个明确的返回出口
        Spacer(Modifier.height(20.dp))
        BlackPillButton(
            label = stringResource(R.string.exec_empty_action),
            onClick = onBack,
            modifier = Modifier.padding(horizontal = 24.dp),
            height = 48.dp,
        )
    }
}

@Composable
private fun a11yCountdown(remainingMs: Long): String {
    val totalSec = (remainingMs / 1000).toInt()
    return stringResource(R.string.exec_cd_ring_fmt, totalSec / 60, totalSec % 60)
}

/** 延迟快捷键（M11）：整任务后移，拟我戏剧性回拨时间条 */
@Composable
private fun DelayChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(Color(0x14000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, style = PastelleType.Caption, color = Ink)
    }
}
