package com.pastelle.todo.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动效令牌（规格 7.4，全 App 统一）：
 * SpringBouncy / SpringStandard / PressScale / TweenFast·Med·Slow / StaggerStep。
 */
object Motion {
    /** 进度条 / 光球到达的回弹弹簧 */
    val SpringBouncy: SpringSpec<Float> =
        spring(dampingRatio = 0.38f, stiffness = 300f)

    /** 卡片、吸附等标准弹簧 */
    val SpringStandard: SpringSpec<Float> =
        spring(dampingRatio = 0.55f, stiffness = 380f)

    /** 所有可点元素按压缩放 */
    const val PressScale = 0.96f

    const val TweenFastMs = 180
    const val TweenMedMs = 280
    const val TweenSlowMs = 450

    /** 列表级联间隔 */
    const val StaggerStepMs = 35

    val Ease = FastOutSlowInEasing

    fun <T> tweenFast() = tween<T>(TweenFastMs, easing = Ease)

    fun <T> tweenMed() = tween<T>(TweenMedMs, easing = Ease)

    fun <T> tweenSlow() = tween<T>(TweenSlowMs, easing = Ease)

    /** 列表级联延迟 */
    fun stagger(index: Int): Int = index * StaggerStepMs
}
