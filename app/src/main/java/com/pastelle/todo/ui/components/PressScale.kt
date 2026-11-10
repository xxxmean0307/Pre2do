package com.pastelle.todo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pastelle.todo.core.audio.IosSounds
import com.pastelle.todo.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全 App 统一按压反馈：按下 scale 0.96、松手 spring 回 1.0（规格 7.4），
 * 并在按下的瞬间播一枚 iOS 风格音效（全 App 音源统一挂点；传 null 静音）。
 * 与 [androidx.compose.foundation.clickable] 共用同一个 interactionSource 即可联动。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    sound: IosSounds.Cue? = IosSounds.Cue.Tick,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) Motion.PressScale else 1f,
        animationSpec = Motion.SpringStandard,
        label = "press-scale",
    )
    LaunchedEffect(pressed) {
        if (pressed && enabled && sound != null) IosSounds.play(sound)
    }
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 卡片统一入场：淡入 + 上移 12dp + scale 0.96→1，SpringStandard，
 * 按列表顺序每项延迟 StaggerStep（35ms）级联（规格 7.4）。
 */
@Composable
fun Modifier.cardEntrance(index: Int = 0, enabled: Boolean = true): Modifier {
    val alpha = remember { Animatable(if (enabled) 0f else 1f) }
    val rise = remember { Animatable(if (enabled) 12f else 0f) }
    val scale = remember { Animatable(if (enabled) 0.96f else 1f) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            alpha.snapTo(1f); rise.snapTo(0f); scale.snapTo(1f)
            return@LaunchedEffect
        }
        delay(Motion.stagger(index).toLong())
        launch { alpha.animateTo(1f, Motion.tweenMed()) }
        launch { rise.animateTo(0f, Motion.SpringStandard) }
        launch { scale.animateTo(1f, Motion.SpringStandard) }
    }
    return this.graphicsLayer {
        this.alpha = alpha.value
        scaleX = scale.value
        scaleY = scale.value
        translationY = rise.value.dp.toPx()
    }
}
