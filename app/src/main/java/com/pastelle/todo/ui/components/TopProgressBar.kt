package com.pastelle.todo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pastelle.todo.R
import com.pastelle.todo.ui.theme.Dimens
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.Motion
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.ProgressFillEnd
import com.pastelle.todo.ui.theme.ProgressFillStart
import com.pastelle.todo.ui.theme.Track
import kotlin.math.roundToInt

/**
 * 今日进度条（规格 4.1 第 2 项，光球飞入的目标组件）：
 * 16dp 胶囊、轨道 Track、填充 #6FE09A→#4FC14F 绿渐变、表面缓慢流动的高光（shine sweep）；
 * 右侧百分比数字，下方一行小字 "今日进度 42%・已完成 3/7"。
 */
@Composable
fun TopProgressBar(
    progress: Float,
    completedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = Motion.SpringBouncy,
        label = "top-progress",
    )
    // 百分比滚动（奖励命中后 600ms 平滑滚动到新值，规格 7.3）
    val percentAnim by animateFloatAsState(
        targetValue = fraction * 100f,
        animationSpec = tween(600),
        label = "top-percent-roll",
    )
    val percent = percentAnim.roundToInt()

    Column(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                // 报告光球飞行目标（奖励动画，规格 7.3）
                val pos = coords.positionInRoot()
                com.pastelle.todo.ui.reward.RewardTarget.centerInRoot =
                    androidx.compose.ui.geometry.Offset(
                        pos.x + coords.size.width / 2f,
                        pos.y + coords.size.height / 2f,
                    )
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressCapsule(
                fraction = animated,
                modifier = Modifier
                    .weight(1f)
                    .height(Dimens.ProgressBarHeight),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$percent%",
                style = PastelleType.Percent,
                color = Ink,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.home_progress_fmt, percent, completedCount, totalCount),
            style = PastelleType.Caption,
            color = InkSoft,
        )
    }
}

/** 胶囊进度槽：轨道 + 绿渐变填充 + 流动高光 */
@Composable
private fun ProgressCapsule(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val shine by rememberInfiniteTransition(label = "shine").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "shine-phase",
    )

    Canvas(modifier) {
        val h = size.height
        val w = size.width
        val corner = CornerRadius(h / 2f, h / 2f)

        // 轨道
        drawRoundRect(color = Track, cornerRadius = corner, size = Size(w, h))

        val fillW = w * fraction
        if (fillW > 1f) {
            val fillPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(0f, 0f, fillW, h),
                        cornerRadius = corner,
                    )
                )
            }
            clipPath(fillPath) {
                // 绿渐变填充
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(ProgressFillStart, ProgressFillEnd),
                        startX = 0f,
                        endX = fillW,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(fillW, h),
                )
                // 缓慢流动的高光
                val bandW = h * 1.4f
                val x = (fillW + bandW) * shine - bandW
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.45f),
                            Color.Transparent,
                        ),
                        start = Offset(x, 0f),
                        end = Offset(x + bandW, h),
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(bandW, h),
                )
            }
        }
    }
}
