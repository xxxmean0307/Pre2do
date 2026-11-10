package com.pastelle.todo.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pastelle.todo.R
import com.pastelle.todo.core.audio.IosSounds
import com.pastelle.todo.ui.components.pressScale
import com.pastelle.todo.ui.theme.BlueSoft
import com.pastelle.todo.ui.theme.GreenSoft
import com.pastelle.todo.ui.theme.Ink
import com.pastelle.todo.ui.theme.InkSoft
import com.pastelle.todo.ui.theme.Motion
import com.pastelle.todo.ui.theme.PastelleShapes
import com.pastelle.todo.ui.theme.PastelleType
import com.pastelle.todo.ui.theme.PeachSoft
import com.pastelle.todo.ui.theme.PurpleSoft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 底部四个 Tab（规格 3.6）：今日 / 日程 / 统计 / 我的 */
enum class TabItem(
    val route: String,
    val labelRes: Int,
    val iconOutlined: ImageVector,
    val iconFilled: ImageVector,
    val pillColor: Color,
) {
    Home("home", R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home, PurpleSoft),
    Schedule("schedule", R.string.tab_schedule, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth, BlueSoft),
    Stats("stats", R.string.tab_stats, Icons.Outlined.BarChart, Icons.Filled.BarChart, PeachSoft),
    More("more", R.string.tab_more, Icons.Outlined.GridView, Icons.Filled.GridView, GreenSoft),
}

/**
 * 底部导航栏（iOS 26 Liquid Glass 悬浮长椭圆胶囊）：
 * 不再是高不透明度的奶白霜底，而是「低 alpha 玻璃体积层 + 折射色晕 + 镜面边缘 +
 * 棱镜色散细线 + 缓慢横移的斜光带」——页面内容与流动淡紫背景真实透出，只在玻璃边缘
 * 与高光处收光，靠体积感而非实心底色塑造悬浮层。
 *
 * Android Compose 没有 backdrop-filter（拿不到兄弟节点的像素做背景模糊），因此模糊感由
 * 大半径径向色晕 + 薄雾体积层模拟；高光/折射全部在 drawBehind 的 draw phase 完成，
 * 相位只在绘制阶段读取（每帧重绘、零重组），兼容 minSdk 26 且无需 RenderEffect。
 *
 * 选中 = 图标上浮进半透明 pastel 玻璃药丸（含镜面高光）、图标变深色实心、spring 弹跳；点按播 Tock。
 */
@Composable
fun BottomBar(
    modifier: Modifier = Modifier,
    currentRoute: String?,
    onSelect: (TabItem) -> Unit,
) {
    val capsule = RoundedCornerShape(percent = 50)   // 长椭圆（全圆角）胶囊

    // 液态玻璃时钟：14 秒一圈，斜光带与折射色晕缓慢流动（与全 App 流动背景同一节奏语言）
    val phase = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    phase.value = (phase.value + dt / 14f) % 1f
                }
                last = now
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    20.dp, capsule, clip = false,
                    ambientColor = Color(0x24B6A6E0), spotColor = Color(0x1CF3B6C4),
                )
                .clip(capsule)
                .drawBehind { drawLiquidGlassBody(phase.value) },
        ) {
            // 四个 Tab（内容层，玻璃高光不上浮遮挡文字）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabItem.entries.forEach { tab ->
                    TabCell(
                        tab = tab,
                        selected = currentRoute == tab.route,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // 镜面边缘 + 色散：置于最上层，压在内容之上（不吃点击）
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind { drawLiquidGlassRim() }
            )
        }
    }
}

/** 玻璃体内的折射色斑：与 PastelBackground 的淡紫/品红/粉同族 */
private data class GlassBlob(
    val cx: Float,
    val cy: Float,
    val ax: Float,
    val ay: Float,
    val phase: Float,
    val color: Color,
)

private val GlassBlobs = listOf(
    GlassBlob(0.18f, 0.12f, 0.14f, 0.20f, 0.00f, Color(0xFFB79CFF)),
    GlassBlob(0.76f, 0.86f, 0.16f, 0.22f, 0.34f, Color(0xFF8AB8FF)),
    GlassBlob(0.48f, 0.46f, 0.20f, 0.24f, 0.66f, Color(0xFFE89BC8)),
)

/** 玻璃体积层：薄雾 + 折射色晕 + 顶部镜面 + 底缘反弹光 + 流动斜光带 */
private fun DrawScope.drawLiquidGlassBody(phase: Float) {
    val w = size.width
    val h = size.height
    val tau = (2.0 * PI).toFloat()

    // ① 薄雾体积层：与全 App 流动淡紫背景同族的有色玻璃（不是纯白霜）。
    //    低不透明度让背景色真实透出，同时把延伸到胶囊下的页面文字柔化到不干扰 tab 标签
    //    ——相当于 iOS backdrop-blur 的「细节弱化」，但不需要读取兄弟节点像素。
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFFFBF7FF).copy(alpha = 0.62f),
            0.44f to Color(0xFFF3ECFA).copy(alpha = 0.46f),
            1f to Color(0xFFEFE6F7).copy(alpha = 0.60f),
        )
    )

    // ② 折射色晕：大半径径向渐变模拟背景被玻璃柔化、放大的液态色散
    for (b in GlassBlobs) {
        val a = (phase + b.phase) * tau
        val cx = (b.cx + sin(a) * b.ax) * w
        val cy = (b.cy + cos(a * 0.9f) * b.ay) * h
        val r = h * 1.35f
        drawCircle(
            brush = Brush.radialGradient(
                0f to b.color.copy(alpha = 0.16f),
                0.58f to b.color.copy(alpha = 0.06f),
                1f to Color.Transparent,
                center = Offset(cx, cy),
                radius = r,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
    }

    // ③ 顶部镜面高光：只占上沿窄带，中段保持通透
    clipRect(0f, 0f, w, h * 0.34f) {
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.54f),
                1f to Color.Transparent,
                startY = 0f,
                endY = h * 0.34f,
            )
        )
    }

    // ④ 底缘环境光反弹（玻璃厚度感）
    clipRect(0f, h * 0.76f, w, h) {
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.White.copy(alpha = 0.40f),
                startY = h * 0.76f,
                endY = h,
            )
        )
    }

    // ⑤ 缓慢横移的斜光带（Liquid Glass 标志性 specular sweep）
    val sweep = ((phase * 1.15f) % 1.4f - 0.2f) * w
    val band = w * 0.26f
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.22f),
            1f to Color.Transparent,
            start = Offset(sweep - band, 0f),
            end = Offset(sweep + band, h),
        )
    )
}

/** 玻璃轮廓：主镜面描边 + 内缘反弹光 + 冷/暖棱镜色散细线 */
private fun DrawScope.drawLiquidGlassRim() {
    val w = size.width
    val h = size.height
    val r = h / 2f
    val sw = 1.4.dp.toPx()

    // ① 主镜面描边：左上最亮、右下环境光反弹
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.96f),
            0.32f to Color.White.copy(alpha = 0.24f),
            0.64f to Color.White.copy(alpha = 0.10f),
            1f to Color.White.copy(alpha = 0.74f),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        ),
        topLeft = Offset(sw / 2f, sw / 2f),
        size = Size(w - sw, h - sw),
        cornerRadius = CornerRadius(r - sw / 2f),
        style = Stroke(width = sw),
    )

    // ② 内缘柔和亮环：玻璃厚度（上暗下亮）
    val thick = 3.2.dp.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.04f),
            1f to Color.White.copy(alpha = 0.30f),
        ),
        topLeft = Offset(thick / 2f, thick / 2f),
        size = Size(w - thick, h - thick),
        cornerRadius = CornerRadius(r - thick / 2f),
        style = Stroke(width = thick),
    )

    // ③ 底缘内弧反弹光
    clipRect(0f, h * 0.58f, w, h) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.White.copy(alpha = 0.58f),
                startY = h * 0.58f,
                endY = h,
            ),
            topLeft = Offset(sw, sw),
            size = Size(w - sw * 2f, h - sw * 2f),
            cornerRadius = CornerRadius(r - sw),
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }

    // ④ 棱镜色散：内侧两道极细冷/暖偏色线，模拟玻璃分光
    val coldInset = 2.3.dp.toPx()
    drawRoundRect(
        color = Color(0x3C7FE8FF),
        topLeft = Offset(coldInset, coldInset),
        size = Size(w - coldInset * 2f, h - coldInset * 2f),
        cornerRadius = CornerRadius(r - coldInset),
        style = Stroke(width = 0.8.dp.toPx()),
    )
    val warmInset = 3.7.dp.toPx()
    drawRoundRect(
        color = Color(0x32FFA8E0),
        topLeft = Offset(warmInset, warmInset),
        size = Size(w - warmInset * 2f, h - warmInset * 2f),
        cornerRadius = CornerRadius(r - warmInset),
        style = Stroke(width = 0.8.dp.toPx()),
    )
}

@Composable
private fun TabCell(
    tab: TabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(tab.labelRes)
    val interactionSource = remember { MutableInteractionSource() }

    val iconScale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            iconScale.snapTo(0.8f)
            iconScale.animateTo(1f, Motion.SpringBouncy) // 轻微 spring 弹跳
        }
    }
    val pillScale = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected) {
        pillScale.animateTo(if (selected) 1f else 0f, Motion.SpringBouncy)
    }
    val lift by animateFloatAsState(
        targetValue = if (selected) -3f else 0f,
        animationSpec = Motion.SpringBouncy,
        label = "tab-lift",
    )

    Column(
        modifier = modifier
            .clip(PastelleShapes.Small)
            .pressScale(interactionSource, sound = IosSounds.Cue.Tock)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(width = 58.dp, height = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 选中态：半透明 pastel 玻璃凸起（体积色 + 顶部镜面 + 亮边），不再是实心药丸
            Box(
                Modifier
                    .size(width = 58.dp, height = 32.dp)
                    .graphicsLayer {
                        scaleX = pillScale.value
                        scaleY = pillScale.value
                        alpha = pillScale.value
                    }
                    .clip(PastelleShapes.Pill)
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                0f to tab.pillColor.copy(alpha = 0.50f),
                                1f to tab.pillColor.copy(alpha = 0.22f),
                            )
                        )
                        clipRect(0f, 0f, size.width, size.height * 0.5f) {
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.White.copy(alpha = 0.52f),
                                    1f to Color.Transparent,
                                    startY = 0f,
                                    endY = size.height * 0.5f,
                                )
                            )
                        }
                    }
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.92f),
                            0.55f to Color.White.copy(alpha = 0.24f),
                            1f to Color.White.copy(alpha = 0.70f),
                        ),
                        PastelleShapes.Pill,
                    )
            )
            Icon(
                imageVector = if (selected) tab.iconFilled else tab.iconOutlined,
                contentDescription = null,
                tint = if (selected) Ink else InkSoft,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                        translationY = lift.dp.toPx()
                    },
            )
        }
        Spacer(Modifier.height(2.dp))
        // 白色微发光：透明玻璃下仍要与透出的背景文字拉开对比（不改玻璃本身的通透度）
        Text(
            text = label,
            style = PastelleType.TabLabel.copy(
                shadow = Shadow(
                    color = Color.White.copy(alpha = 0.92f),
                    offset = Offset.Zero,
                    blurRadius = 5f,
                ),
            ),
            color = if (selected) Ink else InkSoft,
        )
    }
}
