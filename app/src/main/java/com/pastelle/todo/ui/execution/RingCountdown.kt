package com.pastelle.todo.ui.execution

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.pastelle.todo.ui.theme.Dimens
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.Track
import kotlin.math.PI
import kotlin.math.sin

/**
 * 环形倒计时（规格 4.2）：圆环进度（渐变色）+ 环内水波缓动 + 中央 mm:ss。
 * 每分钟变化的无障碍描述由 liveRegion 播报（规格 9.8）。
 */
@Composable
fun RingCountdown(
    progress: Float,
    centerTime: String,
    ringColors: List<Color>,
    modifier: Modifier = Modifier,
    ringWidth: Dp = Dimens.ExecutionRingWidth,
    waveColor: Color? = null,
    accessibilityLabel: String = centerTime,
) {
    val wavePhase by rememberInfiniteTransition(label = "ring-wave").animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "ring-wave-phase",
    )

    Box(modifier.size(Dimens.ExecutionRing), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(Dimens.ExecutionRing)) {
            val stroke = Stroke(width = ringWidth.toPx(), cap = StrokeCap.Round)
            val inset = ringWidth.toPx() / 2f
            val arcSize = Size(size.width - ringWidth.toPx(), size.height - ringWidth.toPx())

            // 轨道
            drawArc(
                color = Track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            // 渐变进度环
            drawArc(
                brush = Brush.sweepGradient(ringColors),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )

            // 环内水波（缓慢起伏）
            if (waveColor != null) {
                val innerR = (size.minDimension - ringWidth.toPx() * 2f) / 2f
                val circlePath = Path().apply {
                    addOval(
                        Rect(
                            size.width / 2f - innerR,
                            size.height / 2f - innerR,
                            size.width / 2f + innerR,
                            size.height / 2f + innerR,
                        )
                    )
                }
                clipPath(circlePath) {
                    val baseY = size.height * (0.70f - 0.04f * progress)
                    val amp = size.height * 0.018f
                    val wavePath = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(0f, baseY)
                        var x = 0f
                        while (x <= size.width) {
                            val y = baseY + sin(x / size.width * 4f * PI.toFloat() + wavePhase) * amp
                            lineTo(x, y)
                            x += 8f
                        }
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(wavePath, color = waveColor.copy(alpha = 0.35f))
                }
            }
        }
        Text(
            text = centerTime,
            style = PastelleType.Timer,
            color = Ink,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = accessibilityLabel
            },
        )
    }
}
