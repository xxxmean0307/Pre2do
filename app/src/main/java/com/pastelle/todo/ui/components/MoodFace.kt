package com.pastelle.todo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.pastelle.todo.ui.theme.Blue
import com.pastelle.todo.ui.theme.CardWhite
import com.pastelle.todo.ui.theme.Green
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.Motion
import com.pastelle.todo.ui.theme.Peach
import com.pastelle.todo.ui.theme.Pink
import com.pastelle.todo.ui.theme.Track
import androidx.compose.foundation.layout.size
import kotlin.math.cos
import kotlin.math.sin

/**
 * 表情脸（规格 3.5）：Canvas 自绘圆角方形小脸（圆角 14dp 级、纯色底 + 简单眉眼）。
 * 30% 蓝脸犯困 / 60% 桃脸微笑 / 100% 绿脸大笑 + 星星眼；Neutral 为灰底中性脸（无完成记录的日子）。
 */
enum class MoodLevel { Sleepy, Smile, Star, Neutral }

/** 按当日平均完成度映射表情档位（有完成记录时：<45% 犯困、45–80% 微笑、≥80% 星星眼） */
fun moodLevelFor(avgCompletionPct: Float, hasCompletions: Boolean): MoodLevel = when {
    !hasCompletions -> MoodLevel.Neutral
    avgCompletionPct >= 80f -> MoodLevel.Star
    avgCompletionPct >= 45f -> MoodLevel.Smile
    else -> MoodLevel.Sleepy
}

@Composable
fun MoodFace(
    level: MoodLevel,
    faceSize: Dp,
    modifier: Modifier = Modifier,
) {
    val background = when (level) {
        MoodLevel.Sleepy -> Blue
        MoodLevel.Smile -> Peach
        MoodLevel.Star -> Green
        MoodLevel.Neutral -> Track
    }
    val description = when (level) {
        MoodLevel.Sleepy -> "完成度表情：还需加油"
        MoodLevel.Smile -> "完成度表情：还不错"
        MoodLevel.Star -> "完成度表情：非常棒"
        MoodLevel.Neutral -> "完成度表情：暂无记录"
    }
    Canvas(
        modifier
            .size(faceSize)
            .semantics { contentDescription = description }
    ) {
        val s = size.minDimension
        val radius = s * (14f / 40f)
        // 脸底：圆角方形
        drawRoundRect(
            color = background,
            cornerRadius = CornerRadius(radius, radius),
            size = Size(s, s),
        )

        val eyeY = s * 0.42f
        val leftEyeX = s * 0.34f
        val rightEyeX = s * 0.66f
        val stroke = Stroke(width = s * 0.075f, cap = StrokeCap.Round)

        when (level) {
            MoodLevel.Sleepy -> {
                // 眯眼：两条短横线
                drawLine(
                    color = Ink,
                    start = Offset(leftEyeX - s * 0.08f, eyeY),
                    end = Offset(leftEyeX + s * 0.08f, eyeY),
                    strokeWidth = s * 0.07f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Ink,
                    start = Offset(rightEyeX - s * 0.08f, eyeY),
                    end = Offset(rightEyeX + s * 0.08f, eyeY),
                    strokeWidth = s * 0.07f,
                    cap = StrokeCap.Round,
                )
                // 嘴角微撇
                drawArc(
                    color = Ink,
                    startAngle = 160f,
                    sweepAngle = 40f,
                    useCenter = false,
                    topLeft = Offset(s * 0.36f, s * 0.58f),
                    size = Size(s * 0.28f, s * 0.18f),
                    style = stroke,
                )
            }
            MoodLevel.Smile -> {
                // 圆眼
                drawCircle(Ink, radius = s * 0.055f, center = Offset(leftEyeX, eyeY))
                drawCircle(Ink, radius = s * 0.055f, center = Offset(rightEyeX, eyeY))
                // 微笑弧
                drawArc(
                    color = Ink,
                    startAngle = 10f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(s * 0.33f, s * 0.52f),
                    size = Size(s * 0.34f, s * 0.24f),
                    style = stroke,
                )
            }
            MoodLevel.Star -> {
                // 星星眼
                drawStar(Offset(leftEyeX, eyeY), s * 0.10f, Ink)
                drawStar(Offset(rightEyeX, eyeY), s * 0.10f, Ink)
                // 大笑
                drawArc(
                    color = Ink,
                    startAngle = 5f,
                    sweepAngle = 170f,
                    useCenter = false,
                    topLeft = Offset(s * 0.30f, s * 0.52f),
                    size = Size(s * 0.40f, s * 0.28f),
                    style = Stroke(width = s * 0.09f, cap = StrokeCap.Round),
                )
            }
            MoodLevel.Neutral -> {
                drawCircle(Ink, radius = s * 0.05f, center = Offset(leftEyeX, eyeY))
                drawCircle(Ink, radius = s * 0.05f, center = Offset(rightEyeX, eyeY))
                drawLine(
                    color = Ink,
                    start = Offset(s * 0.38f, s * 0.66f),
                    end = Offset(s * 0.62f, s * 0.66f),
                    strokeWidth = s * 0.06f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** 四角星（星星眼） */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color,
) {
    val inner = radius * 0.42f
    val path = Path()
    for (i in 0 until 8) {
        val angle = Math.PI / 4 * i - Math.PI / 2
        val r = if (i % 2 == 0) radius else inner
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
}
