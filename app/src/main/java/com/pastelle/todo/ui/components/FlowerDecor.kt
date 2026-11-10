package com.pastelle.todo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.pastelle.todo.ui.theme.PinkSoft
import com.pastelle.todo.ui.theme.PurpleSoft
import com.pastelle.todo.ui.theme.CardWhite
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 一朵花 / 有机色块的固定种子描述 */
data class DecorSpec(
    val relX: Float,
    val relY: Float,
    val sizeDp: Float,
    val color: Color,
    val alpha: Float,
    val isBlob: Boolean,
    val rotation: Float,
    val wobbleSeed: Int,
    val periodMs: Int,
)

// 屏幕四角与边缘的锚点（相对坐标），保证装饰"包围但不遮挡"内容区
private val decorAnchors = listOf(
    -0.10f to 0.02f,
    0.86f to -0.06f,
    0.40f to -0.10f,
    -0.14f to 0.42f,
    0.90f to 0.52f,
    0.02f to 0.90f,
    0.72f to 0.96f,
    0.34f to 1.02f,
)

/**
 * 固定种子随机生成 4–6 朵五瓣花朵 / 有机色块（白、粉、紫，alpha 0.25–0.55，尺寸 90–180dp）。
 * 相同 seed 每次生成完全相同，保证页面装饰稳定。
 */
fun flowerField(seed: Int, count: Int = 5): List<DecorSpec> {
    val random = Random(seed)
    val palette = listOf(CardWhite, PinkSoft, PurpleSoft)
    return decorAnchors.shuffled(random).take(count).mapIndexed { index, anchor ->
        DecorSpec(
            relX = anchor.first + random.nextFloat() * 0.08f - 0.04f,
            relY = anchor.second + random.nextFloat() * 0.08f - 0.04f,
            sizeDp = 90f + random.nextFloat() * 90f,
            color = palette[(index + seed) % palette.size],
            alpha = 0.25f + random.nextFloat() * 0.30f,
            isBlob = random.nextBoolean(),
            rotation = random.nextFloat() * 360f,
            wobbleSeed = random.nextInt(1, 1000),
            periodMs = 8000 + random.nextInt(0, 5) * 800, // 8–12 秒一周期
        )
    }
}

/**
 * 背景装饰层：Compose Canvas 自绘五瓣花朵与有机色块。
 * floating = true 时花朵做极缓慢的漂浮与呼吸缩放（准备页 / 休息页）。
 */
@Composable
fun FlowerDecor(
    specs: List<DecorSpec>,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "flower-decor")
    val phases: List<Float> = if (floating) {
        specs.map { spec ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = (2f * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(spec.periodMs, easing = LinearEasing),
                ),
                label = "flower-phase-${spec.wobbleSeed}",
            )
            phase
        }
    } else {
        specs.map { 0f }
    }

    Canvas(modifier) {
        specs.forEachIndexed { index, spec ->
            val phase = phases[index]
            val drift = if (floating) sin(phase) * 6.dp.toPx() else 0f   // 位移 ±6dp
            val breathe = if (floating) 1f + 0.03f * cos(phase) else 1f  // 呼吸缩放
            val center = Offset(spec.relX * size.width, spec.relY * size.height + drift)
            val radius = spec.sizeDp.dp.toPx() / 2f * breathe
            rotate(spec.rotation + if (floating) sin(phase).toFloat() * 4f else 0f, center) {
                if (spec.isBlob) {
                    drawOrganicBlob(center, radius, spec.color, spec.alpha, spec.wobbleSeed)
                } else {
                    drawPetalFlower(center, radius, spec.color, spec.alpha)
                }
            }
        }
    }
}

/** 五瓣花朵：5 枚花瓣小圆 + 花心 */
internal fun DrawScope.drawPetalFlower(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
) {
    val petalRadius = radius * 0.52f
    val petalDistance = radius * 0.55f
    for (i in 0 until 5) {
        val angle = -PI / 2 + i * 2 * PI / 5
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = petalRadius,
            center = Offset(
                center.x + (petalDistance * cos(angle)).toFloat(),
                center.y + (petalDistance * sin(angle)).toFloat(),
            ),
        )
    }
    drawCircle(
        color = color.copy(alpha = (alpha * 0.85f).coerceIn(0f, 1f)),
        radius = radius * 0.34f,
        center = center,
    )
}

/** 单朵花插画（空状态 / 装饰用） */
@Composable
fun FlowerBloom(
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    petalColor: Color = CardWhite,
    centerColor: Color = PinkSoft,
) {
    Canvas(modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f * 0.92f
        drawPetalFlower(center, radius, petalColor, alpha = 1f)
        drawCircle(color = centerColor, radius = radius * 0.32f, center = center)
    }
}

/** 有机色块：半径随角度轻微起伏的封闭曲线 */
private fun DrawScope.drawOrganicBlob(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
    wobbleSeed: Int,
) {
    val points = 9
    val path = Path()
    val nodes = (0 until points).map { i ->
        val angle = i * 2 * PI / points
        val wobble = 0.78f + 0.32f * ((sin(i * 1.7f + wobbleSeed * 0.37f) + 1f) / 2f)
        val r = radius * wobble
        Offset(
            center.x + (r * cos(angle)).toFloat(),
            center.y + (r * sin(angle)).toFloat(),
        )
    }
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    path.moveTo(mid(nodes[points - 1], nodes[0]).x, mid(nodes[points - 1], nodes[0]).y)
    for (i in 0 until points) {
        val next = nodes[(i + 1) % points]
        val end = mid(nodes[i], next)
        path.quadraticTo(nodes[i].x, nodes[i].y, end.x, end.y)
    }
    path.close()
    drawPath(path, color = color.copy(alpha = alpha))
    drawPath(path, color = color.copy(alpha = (alpha * 0.6f)), style = Stroke(width = radius * 0.04f))
}
