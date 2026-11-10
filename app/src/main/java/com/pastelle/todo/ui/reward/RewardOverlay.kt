package com.pastelle.todo.ui.reward

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pastelle.todo.R
import com.pastelle.todo.timing.TimingEvent
import com.pastelle.todo.ui.components.MoodFace
import com.pastelle.todo.ui.components.MoodLevel
import com.pastelle.todo.ui.theme.Amber
import com.pastelle.todo.ui.theme.BlackPill
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.Coral
import com.pastelle.todo.ui.theme.Green
import com.pastelle.todo.ui.theme.PastelleElevation
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.Pink
import com.pastelle.todo.ui.theme.ProgressFillEnd
import com.pastelle.todo.ui.theme.ProgressFillStart
import kotlin.math.max
import kotlin.random.Random

/** 光球飞行目标：TopProgressBar 中心（由其自身报告） */
object RewardTarget {
    @Volatile
    var centerInRoot: Offset? = null
}

/** 光球起飞点：滑块拇指中心（由 CompletionSlider 报告；缺省屏幕下方） */
object RewardStart {
    @Volatile
    var centerInRoot: Offset? = null
}

private data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val rot: Float,
    val vrot: Float,
    val sizeDp: Float,
    val color: Color,
)

/**
 * 奖励动画层（规格 7.3）：
 * 光球沿抛物贝塞尔弧线飞向 TopProgressBar（650ms，12 条拖尾），命中绿色涟漪 + 白闪；
 * 进度条 spring 增长 / 百分比滚动由 TopProgressBar 自身承担；
 * 100%：约 60 片彩纸 1400ms + 横幅「太棒了！+{XP} 经验 🎉」（>1 附全勤连击）；
 * 60%：16 个柔和圆点 + 「完成得不错，+{XP} 经验」；30%：光球更小更暗 + 「开了个头也算数…」；
 * 动画期间上层拦截点击；进程被杀由 pendingCelebration 补播一次（9.6）。
 */
@Composable
fun RewardOverlay(
    reward: TimingEvent.Completed?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reward == null) return

    var stage by remember(reward) { mutableStateOf(0) } // 0=飞行 1=命中 2=横幅
    val orbProgress = remember(reward) { Animatable(0f) }
    val trail = remember(reward) { mutableStateListOf<Offset>() }
    val ripple = remember(reward) { Animatable(0f) }
    val particles = remember(reward) { mutableStateListOf<Particle>() }
    val bannerOffset = remember(reward) { Animatable(-140f) }
    val particleAnim = remember(reward) { Animatable(0f) }

    val target = RewardTarget.centerInRoot ?: Offset(540f, 260f)
    val start = RewardStart.centerInRoot ?: Offset(540f, 1500f)
    val control = remember(reward, target, start) {
        Offset((start.x + target.x) / 2f, max(0f, minOf(start.y, target.y) - 220f))
    }

    val gear = reward.gearPct
    val burstCount = when (gear) {
        100 -> 60
        60 -> 16
        else -> 0
    }

    // 时间线：飞行 650ms → 命中涟漪/白闪 → 横幅弹下 → hold 1.8s → 收场
    LaunchedEffect(reward) {
        orbProgress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        stage = 1
        ripple.animateTo(1f, tween(600))
        val random = Random(reward.taskId.hashCode())
        val palette = listOf(Green, ProgressFillStart, ProgressFillEnd, Amber, Coral, Pink)
        repeat(burstCount) {
            particles.add(
                Particle(
                    x = target.x + random.nextFloat() * 120f - 60f,
                    y = target.y + random.nextFloat() * 60f,
                    vx = random.nextFloat() * 8f - 4f,
                    vy = -(random.nextFloat() * 6f + 3f),
                    rot = random.nextFloat() * 360f,
                    vrot = random.nextFloat() * 10f - 5f,
                    sizeDp = if (gear == 100) 8f + random.nextFloat() * 8f else 6f + random.nextFloat() * 4f,
                    color = palette[random.nextInt(palette.size)],
                )
            )
        }
        particleAnim.animateTo(1f, tween(1400))
        stage = 2
        bannerOffset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 260f))
        kotlinx.coroutines.delay(1800)
        bannerOffset.animateTo(-140f, tween(300))
        onFinished()
    }

    val t = orbProgress.value
    val orbPos = quadraticBezier(start, control, target, t)
    if (stage == 0 && trail.size < 12) trail.add(orbPos)
    val pt = particleAnim.value

    Box(modifier.fillMaxSize()) {
        // 拖尾（12 条渐隐小球）
        trail.forEachIndexed { index, offset ->
            Box(
                Modifier
                    .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
                    .graphicsLayer {
                        alpha = (index + 1f) / max(trail.size, 1) * 0.35f * t
                    }
                    .size((8 - index * 0.5f).dp)
                    .clip(CircleShape)
                    .background(if (gear == 30) Coral else ProgressFillStart),
            )
        }

        // 光球（飞行中；30% 档更小更暗）
        if (stage == 0) {
            Box(
                Modifier
                    .offset { IntOffset(orbPos.x.toInt(), orbPos.y.toInt()) }
                    .graphicsLayer {
                        val s = 1f - 0.45f * t
                        scaleX = s
                        scaleY = s
                        alpha = if (gear == 30) 0.75f else 1f
                    }
                    .size(if (gear == 30) 44.dp else 64.dp)
                    .clip(CircleShape)
                    .background(if (gear == 30) Coral.copy(alpha = 0.85f) else ProgressFillStart),
            )
        }

        // 命中涟漪 600ms + 白闪 200ms
        if (stage >= 1) {
            Box(
                Modifier
                    .offset { IntOffset(target.x.toInt(), target.y.toInt()) }
                    .graphicsLayer {
                        val s = 0.4f + ripple.value * 1.6f
                        scaleX = s
                        scaleY = s
                        alpha = (1f - ripple.value) * 0.55f
                    }
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Green),
            )
            Box(
                Modifier
                    .offset { IntOffset(target.x.toInt(), target.y.toInt()) }
                    .graphicsLayer { alpha = (1f - ripple.value) * 0.6f }
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }

        // 彩纸 / 柔和圆点（受重力与旋转）
        particles.forEach { p ->
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (p.x + p.vx * pt * 60f).toInt(),
                            (p.y + p.vy * pt * 60f + 160f * pt * pt).toInt(),
                        )
                    }
                    .graphicsLayer {
                        rotationZ = p.rot + p.vrot * pt * 60f
                        alpha = 1f - pt * 0.6f
                    }
                    .size(p.sizeDp.dp)
                    .clip(RoundedCornerShape(50))
                    .background(p.color),
            )
        }

        // 庆祝横幅（顶部弹下，hold 1.8s 后上滑消失）
        if (stage >= 2) {
            val bannerText = when (gear) {
                100 -> stringResource(R.string.reward_banner_100, reward.xp)
                60 -> stringResource(R.string.reward_banner_60, reward.xp)
                else -> stringResource(R.string.reward_banner_30)
            }
            val streakText = stringResource(R.string.reward_streak_fmt, reward.streak)
            val fullText = if (gear == 100 && reward.streak > 1) "$bannerText・$streakText" else bannerText
            Row2(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, bannerOffset.value.toInt()) }
                    .padding(top = 28.dp),
            ) {
                Box(
                    Modifier
                        .shadow(PastelleElevation.Card, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(CardWhite)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .semantics { contentDescription = fullText },
                ) {
                    Text(text = fullText, style = PastelleType.CardTitle, color = BlackPill)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                MoodFace(
                    level = when (gear) {
                        100 -> MoodLevel.Star
                        60 -> MoodLevel.Smile
                        else -> MoodLevel.Sleepy
                    },
                    faceSize = 36.dp,
                )
            }
        }
    }
}

@Composable
private fun Row2(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        content = { content() },
    )
}

private fun quadraticBezier(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * p0.x + 2f * u * t * p1.x + t * t * p2.x,
        u * u * p0.y + 2f * u * t * p1.y + t * t * p2.y,
    )
}
