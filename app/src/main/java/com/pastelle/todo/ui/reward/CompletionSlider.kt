package com.pastelle.todo.ui.reward

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pastelle.todo.R
import com.pastelle.todo.ui.components.BlackPillButton
import com.pastelle.todo.ui.components.MoodFace
import com.pastelle.todo.ui.components.MoodLevel
import com.pastelle.todo.ui.components.PillTrailingIcon
import com.pastelle.todo.ui.theme.Amber
import com.pastelle.todo.ui.theme.BlackPill
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.Coral
import com.pastelle.todo.ui.theme.Dimens
import com.pastelle.todo.ui.theme.GlowGreen
import com.pastelle.todo.ui.theme.Green
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.PastelleElevation
import com.pastelle.todo.ui.theme.PastelleShapes
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.PinkSoft
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

/** 滑块阶段：隐藏 / 拖动中 / 待确认（✕/✓） */
private enum class SliderPhase { Hidden, Dragging, Confirm }

/** 三个档位：30 / 60 / 100（规格 7.3） */
private val gears = listOf(30, 60, 100)

private fun gearLevel(gear: Int): MoodLevel = when (gear) {
    100 -> MoodLevel.Star
    60 -> MoodLevel.Smile
    else -> MoodLevel.Sleepy
}

/** 松手磁吸到最近档位 */
private fun nearestGear(p: Float): Int = gears.minByOrNull { kotlin.math.abs(it / 100f - p) } ?: 30

/** 磁点 zone：±12% 映射高度（规格 7.2） */
private fun inMagnetZone(p: Float): Boolean =
    gears.any { kotlin.math.abs(it / 100f - p) <= 0.12f }

/** 规格 7.2：完成度连续渐变 Coral → Amber → Green */
private fun fillColorFor(p: Float): Color = when {
    p <= 0.30f -> Coral
    p <= 0.60f -> lerp(Coral, Amber, (p - 0.30f) / 0.30f)
    else -> lerp(Amber, Green, (p - 0.60f) / 0.40f)
}

/**
 * 完成滑块主机组件（规格 7.2）：黑色「完成 ✓」胶囊按钮 + 按住升起的竖向长椭圆滑块。
 * - 按住按钮上滑：0→260dp 映射填充 0%→100%；磁点 30/60/100（±12% 高度）震动 tick；
 * - 填充 Coral→Amber→Green 连续渐变，顶部 10dp 波浪；100% 绿色光晕 + 拇指脉动放大 72dp；
 * - 松手 spring(damping 0.45 / stiffness 380) 弹到最近档位，显示 ✕ / ✓；
 * - 取消 ✕（或往回滑回起点以下）：滑块沉下、任务继续；确认 ✓：先落库再播奖励动画（9.6）；
 * - 无障碍替代：三个大按钮 30% / 60% / 100% 直达确认态（9.8）。
 */
@Composable
fun CompletionSliderHost(
    enabled: Boolean,
    forced: Boolean,
    gateLabel: String,
    onConfirm: (gearPct: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var phase by remember { mutableStateOf(SliderPhase.Hidden) }
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val mapPx = with(LocalDensity.current) { Dimens.SliderFillMap.toPx() }
    val snapAnim = remember { Animatable(0f) }
    val haptics = rememberHaptics()

    // 1.2D 强制：自动弹出完成滑块（不能再拖，规格 5.3）
    LaunchedEffect(forced) {
        if (forced && phase == SliderPhase.Hidden) {
            progress = 0.6f
            snapAnim.snapTo(0.6f)
            phase = SliderPhase.Confirm
        }
    }

    val currentP = if (phase == SliderPhase.Dragging) progress else {
        if (snapAnim.value > 0f) snapAnim.value else progress
    }

    Box(modifier) {
        // 黑色完成按钮（80% 前置灰 + 门槛文案，规格 7.1）
        Box(
            Modifier
                .fillMaxWidth()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // 按住即升起滑块
                        phase = SliderPhase.Dragging
                        progress = 0f
                        var lastY = 0f
                        var totalUp = 0f
                        var started = false
                        var inZone = false
                        var done = false
                        while (!done) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!started) {
                                lastY = change.position.y
                                started = true
                            } else {
                                totalUp += lastY - change.position.y
                                lastY = change.position.y
                                val p = (totalUp / mapPx).coerceIn(0f, 1f)
                                val zone = inMagnetZone(p)
                                if (zone && !inZone) haptics.tick() // 进入磁点 zone：触感 tick
                                inZone = zone
                                progress = p
                            }
                            change.consume()
                            if (!change.pressed) done = true
                        }
                        if (totalUp <= 0f) {
                            // 滑回起点以下 / 松手于起点以下 = 取消
                            phase = SliderPhase.Hidden
                            progress = 0f
                        } else {
                            val target = nearestGear(progress) / 100f
                            scope.launch {
                                snapAnim.snapTo(progress)
                                snapAnim.animateTo(target, spring(dampingRatio = 0.45f, stiffness = 380f))
                            }
                            progress = target
                            phase = SliderPhase.Confirm
                        }
                    }
                },
        ) {
            BlackPillButton(
                label = stringResource(R.string.exec_complete),
                onClick = {
                    if (enabled) {
                        // 纯点击（无障碍等）：直接进确认态，默认 60%
                        progress = 0.6f
                        scope.launch { snapAnim.snapTo(0.6f) }
                        phase = SliderPhase.Confirm
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                trailingIcon = PillTrailingIcon.Check,
            )
        }
        if (!enabled) {
            Text(
                text = gateLabel,
                style = PastelleType.Caption,
                color = InkSoft,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(top = 76.dp),
            )
        }

        // 滑块浮层
        if (phase != SliderPhase.Hidden) {
            SliderOverlay(
                progress = currentP,
                phase = phase,
                onCancel = {
                    phase = SliderPhase.Hidden
                    progress = 0f
                },
                onConfirm = { gear ->
                    haptics.waveform()
                    onConfirm(gear)
                },
                onQuickGear = { gear ->
                    progress = gear / 100f
                    scope.launch { snapAnim.snapTo(gear / 100f) }
                    phase = SliderPhase.Confirm
                    haptics.tick()
                },
            )
        }
    }
}

@Composable
private fun SliderOverlay(
    progress: Float,
    phase: SliderPhase,
    onCancel: () -> Unit,
    onConfirm: (Int) -> Unit,
    onQuickGear: (Int) -> Unit,
) {
    val gear = nearestGear(progress)
    val fillColor = fillColorFor(progress)
    val gearLabel = stringResource(R.string.slider_gear_fmt, gear)
    val confirmDesc = stringResource(R.string.slider_confirm)
    val cancelDesc = stringResource(R.string.slider_cancel)

    // 100% 绿光晕呼吸（alpha 0.4–0.8）
    val glowAlpha by animateFloatAsState(
        targetValue = if (progress >= 0.999f) 0.8f else 0.4f,
        animationSpec = tween(800),
        label = "slider-glow",
    )
    val thumbPulse = if (progress >= 0.999f) 1.05f else 1f

    Box(
        Modifier
            .fillMaxSize()
            .background(PinkSoft.copy(alpha = 0.38f)),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 130.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 档位气泡（表情脸 48dp + 完成度 %），spring 弹出
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .shadow(PastelleElevation.Card, PastelleShapes.Card)
                    .clip(PastelleShapes.Card)
                    .background(CardWhite)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MoodFace(level = gearLevel(gear), faceSize = Dimens.SliderFaceBubble)
                    Spacer(Modifier.height(6.dp))
                    Text(gearLabel, style = PastelleType.TabLabel, color = BlackPill)
                }
            }

            // 竖向长椭圆轨道 340×76（白底、粉描边、阴影）
            Box(
                Modifier
                    .height(Dimens.SliderHeight)
                    .width(Dimens.SliderWidth)
                    .shadow(PastelleElevation.Pill, PastelleShapes.Pill)
                    .clip(PastelleShapes.Pill)
                    .background(CardWhite)
                    .border(Dimens.SliderTrackStroke, PinkSoft, PastelleShapes.Pill),
            ) {
                // 填充 + 顶部 10dp 波浪
                Canvas(Modifier.fillMaxSize()) {
                    val fillH = size.height * progress.coerceIn(0f, 1f)
                    if (fillH > 1f) {
                        val wavePath = Path().apply {
                            moveTo(0f, size.height)
                            lineTo(0f, size.height - fillH)
                            var x = 0f
                            while (x <= size.width) {
                                val y = size.height - fillH +
                                    sin(x / size.width * 2f * PI.toFloat() + 0.6f) *
                                    Dimens.SliderThumbWave.toPx() / 2f
                                lineTo(x, y)
                                x += 6f
                            }
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(
                            wavePath,
                            brush = Brush.verticalGradient(
                                colors = listOf(fillColor.copy(alpha = 0.85f), fillColor),
                                startY = size.height - fillH,
                                endY = size.height,
                            ),
                        )
                    }
                }
                // 100% 绿色光晕（16dp blur 级边缘光）
                if (progress >= 0.999f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = glowAlpha }
                            .border(Dimens.SliderGlowBlur, GlowGreen, PastelleShapes.Pill),
                    )
                }
                // 拇指：白圆 + 填充色描边，随手指上浮；100% 脉动放大 72dp
                val thumbSize = if (progress >= 0.999f) Dimens.SliderThumbActive else Dimens.SliderThumb
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { coords ->
                            // 报告光球起飞点（奖励动画，规格 7.3）
                            val pos = coords.positionInRoot()
                            RewardStart.centerInRoot = androidx.compose.ui.geometry.Offset(
                                pos.x + coords.size.width / 2f,
                                pos.y + coords.size.height / 2f,
                            )
                        }
                        .offset {
                            val trackH = Dimens.SliderHeight.toPx()
                            val y = (trackH * progress.coerceIn(0f, 1f) - thumbSize.toPx() / 2f)
                                .coerceAtLeast(0f)
                            androidx.compose.ui.unit.IntOffset(0, -(y.toInt()))
                        }
                        .size(thumbSize)
                        .graphicsLayer {
                            scaleX = thumbPulse
                            scaleY = thumbPulse
                        }
                        .shadow(PastelleElevation.Pill, CircleShape)
                        .clip(CircleShape)
                        .background(CardWhite)
                        .border(3.dp, fillColor, CircleShape),
                )
            }

            // 确认 ✓ / 取消 ✕（松手后出现）
            if (phase == SliderPhase.Confirm) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .shadow(PastelleElevation.Card, CircleShape)
                            .clip(CircleShape)
                            .background(CardWhite)
                            .clickable(onClick = onCancel)
                            .semantics { contentDescription = cancelDesc },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", style = PastelleType.PageTitle, color = InkSoft)
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .size(Dimens.SliderConfirm)
                            .shadow(PastelleElevation.Pill, CircleShape)
                            .clip(CircleShape)
                            .background(BlackPill)
                            .clickable { onConfirm(gear) }
                            .semantics { contentDescription = confirmDesc },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", style = PastelleType.PageTitle, color = CardWhite)
                    }
                }
            }
        }

        // 无障碍替代：三个大按钮（规格 9.8）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val gearDescs = listOf(
                stringResource(R.string.slider_gear_fmt, 30),
                stringResource(R.string.slider_gear_fmt, 60),
                stringResource(R.string.slider_gear_fmt, 100),
            )
            gears.forEachIndexed { index, g ->
                Box(
                    Modifier
                        .shadow(PastelleElevation.Card, PastelleShapes.Pill)
                        .clip(PastelleShapes.Pill)
                        .background(CardWhite)
                        .clickable { onQuickGear(g) }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .semantics { contentDescription = gearDescs[index] },
                ) {
                    Text(gearDescs[index], style = PastelleType.TabLabel, color = BlackPill)
                }
            }
        }
    }
}

// ── 触感反馈 ─────────────────────────────────────────

private class Haptics(private val vibrator: Vibrator?) {
    fun tick() {
        vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun waveform() {
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), -1))
    }
}

@Composable
private fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        Haptics(vibrator)
    }
}
