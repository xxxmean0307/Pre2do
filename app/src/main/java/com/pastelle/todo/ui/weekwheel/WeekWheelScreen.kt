package com.pastelle.todo.ui.weekwheel

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pastelle.todo.PastelleApp
import com.pastelle.todo.R
import com.pastelle.todo.core.TaskColor
import com.pastelle.todo.core.TaskDisplayStatus
import com.pastelle.todo.core.TimeUtils
import com.pastelle.todo.core.displayStatus
import com.pastelle.todo.core.PastelleConstants
import com.pastelle.todo.data.db.TaskEntity
import com.pastelle.todo.data.db.TaskState
import com.pastelle.todo.ui.components.TaskCardSurface
import com.pastelle.todo.ui.components.TaskTimelineCard
import com.pastelle.todo.ui.components.drawVideoScrim
import com.pastelle.todo.ui.importflow.ImportEntrySheet
import com.pastelle.todo.ui.importflow.rememberImportViewModel
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalClipboardManager
import com.pastelle.todo.ui.theme.mainColor
import java.time.LocalDate
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import com.pastelle.todo.core.audio.IosSounds
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/* ═════════════════════════════════════════════════════════════
 * 周计划轮盘 v2（视觉打磨版）
 * ① 七日长圆角卡惯性轮盘：居中最大、连续缩放换位
 * ② 贯穿线滑块分割任务：拖动=分割时间（上小下大），虚线牵时间筹码
 * ③ 点块 = 编辑任务（名/时间/颜色/删除）；长按块 = 速删确认；＋增块
 * ④ 毛玻璃质感 + 配色任务块 + 小时刻度 + 空态/操作提示
 * ═════════════════════════════════════════════════════════════ */

// ── 模型 ──────────────────────────────────────────────────

data class WheelBlock(
    val id: String,
    val title: String,
    val startMin: Int,
    val endMin: Int,
    val colorIdx: Int = 0,
    val done: Boolean = false,
)

data class WheelDay(
    val date: String,
    val label: String,
    val blocks: List<WheelBlock>,
)

// ── 常量 ──────────────────────────────────────────────────

private val CARD_R = 32.dp
private const val TRACK_TOP = 390 // 06:00
private const val TRACK_SPAN = 960 // 06:00–22:00
private const val TRACK_Y0 = 96f // 轨道顶端（dp 数值）

/**
 * ★ 卡片几何（M27）：与屏幕等比例 —— 宽高同倍缩放，任何机型都保持屏幕的比例与体量。
 * 解构顺序 (CARD_W, CARD_H, TRACK_H, LABEL_ZONE) 与旧常量同名同义，调用点零改动。
 */
private data class CardGeom(val w: Dp, val h: Dp, val trackH: Dp, val labelZone: Float)

@Composable
private fun cardGeom(): CardGeom {
    val cfg = LocalConfiguration.current
    return remember(cfg.screenWidthDp, cfg.screenHeightDp) {
        val w = cfg.screenWidthDp * 0.72f
        val h = cfg.screenHeightDp * 0.72f   // w/h == screenWidthDp/screenHeightDp ⇒ 与屏幕等比例
        CardGeom(w = w.dp, h = h.dp, trackH = (h * 0.70f).dp, labelZone = h * 0.16f)
    }
}

private val Ink = Color(0xFF3D3A44)
private val InkSoft = Color(0xFF8B8797)
private val PALETTE = listOf(
    Color(0xFFF3B6C4), // pink
    Color(0xFFB6A6E0), // purple
    Color(0xFFF6C89A), // peach
    Color(0xFFA6C8E8), // blue
    Color(0xFFA8D8B9), // green
)
private val PALETTE_NAMES = listOf("pink", "purple", "peach", "blue", "green")
private val DAY_LABELS = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

private fun fmt(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

/** 当日任务占位区间：卡片轨道映射 [e0,e1]，任务块严格按时间占比铺满卡片 */
private fun extentOf(blocks: List<WheelBlock>): IntArray {
    if (blocks.isEmpty()) return intArrayOf(TRACK_TOP, TRACK_TOP + TRACK_SPAN)
    val e0 = blocks.minOf { it.startMin }
    val e1 = maxOf(blocks.maxOf { it.endMin }, e0 + 60)
    return intArrayOf(e0, e1)
}

private fun yNorm(min: Int, e0: Int, e1: Int): Float =
    ((min - e0).coerceIn(0, e1 - e0)) / (e1 - e0).toFloat()

// ── 屏幕 ──────────────────────────────────────────────────

@Composable
fun WeekWheelScreen(
    onOpenPreview: () -> Unit = {},
    onCardModeChange: (Boolean) -> Unit = {},
    editRequest: Boolean = false,
    onEditRequestHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as PastelleApp }
    val repo = remember { app.repository }

    // ★ 导入日程：入口卡（粘贴剪贴板 / 选择文件）→ 解析 → 预览页；与旧日程页同一活动级状态机
    val importVm = rememberImportViewModel()
    val clipboard = LocalClipboardManager.current
    var showImportSheet by remember { mutableStateOf(false) }
    val importFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val text = uri?.let { picked ->
            try {
                context.contentResolver.openInputStream(picked)
                    ?.use { it.readBytes().decodeToString() }
            } catch (e: Exception) {
                null
            }
        }
        importVm.parse(text)
        onOpenPreview()
    }

    val today = LocalDate.now()
    val weekStart = today.minusDays((today.dayOfWeek.value % 7).toLong())
    val days = remember {
        mutableStateListOf<WheelDay>().apply {
            for (i in 0..6) {
                val d = weekStart.plusDays(i.toLong())
                add(WheelDay(d.format(DateTimeFormatter.ISO_LOCAL_DATE), DAY_LABELS[i], emptyList()))
            }
        }
    }
    var loaded by remember { mutableStateOf(false) }
    // ★ M32：写库与页面生命周期解耦。旧实现把 500ms 防抖挂在页面 LaunchedEffect 上，
    //   切 Tab / 返回 / 进程回收会直接取消协程，整批编辑丢失；改挂 Application 级 scope，
    //   并在「完成 / 返回 / 外划 / 页面销毁」四个出口立即 flush 当前快照。
    val appScope = remember { app.appScope }
    var saveJob by remember { mutableStateOf<Job?>(null) }

    fun flushDays(snapshot: List<WheelDay>) {
        appScope.launch {
            snapshot.forEach { day ->
                repo.saveWheelDay(
                    date = day.date,
                    title = "周计划",
                    tasks = day.blocks.mapIndexed { order, b -> taskOf(day.date, b, order) },
                )
            }
        }
    }

    fun scheduleSave() {
        if (!loaded) return
        saveJob?.cancel()
        val snapshot = days.toList()
        saveJob = appScope.launch {
            delay(250)
            flushDays(snapshot)
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        saveJob = null
        flushDays(days.toList())
    }

    DisposableEffect(Unit) {
        onDispose { if (loaded) saveNow() }
    }

    // ★ 两态：展示态（任务卡片核心）/ 星期卡片编辑态（轮盘）；双指向内划进、外划/「完成」出
    var editing by remember { mutableStateOf(false) }
    var selDate by remember { mutableStateOf(today) }
    LaunchedEffect(editing) { onCardModeChange(editing) }
    // 外部入口（今日页「安排今天」/ 小组件「去安排」）：直接落到今天的编辑态
    LaunchedEffect(editRequest) {
        if (editRequest) {
            selDate = today
            editing = true
            onEditRequestHandled()
        }
    }
    // ★ M24 内容形变：展示态任务卡窗口位（进入过渡前冻结）+ 根容器窗口位（换算到本地坐标）
    val dispRects = remember { mutableStateMapOf<String, Rect>() }
    var rootWinOff by remember { mutableStateOf(Offset.Zero) }
    var rootH by remember { mutableStateOf(0f) }

    LaunchedEffect(editing) {
        // ★ 只在「进入」编辑态时从库里重载。退出时重载会拿 500ms 防抖尚未落库的旧数据
        //   覆盖刚编辑过的 days，导致改动丢失（返回/「完成」都会触发一次 editing=false）。
        if (!editing) return@LaunchedEffect
        for (i in 0..6) {
            val day = days[i]
            val blocks = repo.tasksOfDate(day.date).sortedBy { it.orderIndex }.map {
                WheelBlock(
                    it.id,
                    it.title,
                    it.startMinute,
                    it.startMinute + it.durationMin,
                    // ★ 只有「真实落过完成记录」才算已完成：state=COMPLETED 但 completedAt 为空
                    //   属于异常/遗留数据（如旧测试数据、编辑时间后残留），不能显示「已完成」。
                    done = it.state == TaskState.COMPLETED && it.completedAt != null,
                    // ★ 载入必须还原调色板色号（否则每次进编辑态重载都把自选色归零成粉）
                    colorIdx = PALETTE_NAMES.indexOf(it.color).coerceAtLeast(0),
                )
            }
            if (blocks.isNotEmpty()) days[i] = day.copy(blocks = coalesce(blocks))
        }
        loaded = true
    }
    // 编辑态的改动写入由 scheduleSave() / saveNow() 负责（见上方 appScope 说明），
    // 不再用页面级 LaunchedEffect 防抖，避免离开页面时协程被取消。

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = today.dayOfWeek.value % 7)
    val snap = rememberSnapFlingBehavior(lazyListState = listState)
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val (CARD_W, CARD_H, TRACK_H, LABEL_ZONE) = cardGeom()
    val sidePad = (screenW - CARD_W) / 2

    // 进入编辑态：轮盘定位到展示态所选日（静息相位=卡面左沿贴 sidePad ⇒ 卡面正好居中）
    LaunchedEffect(editing) {
        if (editing) listState.scrollToItem(selDate.dayOfWeek.value % 7)
    }
    // 退出编辑态时，把轮盘居中日带回展示态
    fun syncSelFromWheel() {
        val vp = listState.layoutInfo
        val center = (vp.viewportStartOffset + vp.viewportEndOffset) / 2f
        val info = vp.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }
        if (info != null) selDate = LocalDate.parse(days[info.index].date)
    }
    // ★ 编辑态接管系统返回键：退回本页「展示态」，而不是让 NavHost 直接 pop 回今日页。
    //   （旧行为还会让 wheelCardMode 残留 true ⇒ 今日页底部胶囊栏消失、内容贴底）
    BackHandler(enabled = editing) {
        saveNow()
        syncSelFromWheel()
        editing = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootWinOff = it.boundsInWindow().topLeft; rootH = it.size.height.toFloat() }
            .pointerInput(editing) {
                // 双指手势：以起手双指距为基线的绝对比判据（每手势独立，不跨手势积污）
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var startDist = -1f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val pts = ev.changes.filter { it.pressed }
                        if (pts.isEmpty()) break
                        if (pts.size >= 2) {
                            val d = (pts[0].position - pts[1].position).getDistance()
                            if (startDist <= 0f) startDist = d
                            if (startDist > 0f) {
                                val ratio = d / startDist
                                if (!editing && ratio < 0.8f) {
                                    IosSounds.play(IosSounds.Cue.Pop)
                                    editing = true
                                    break
                                } else if (editing && ratio > 1.25f) {
                                    IosSounds.play(IosSounds.Cue.Swoosh)
                                    saveNow()
                                    syncSelFromWheel()
                                    editing = false
                                    break
                                }
                            }
                        }
                    }
                }
            }
    ) {
        GlassBackdrop()
        // ★ 沉入/浮出过渡：展示态 ⇄ 星期卡片（缩放 + 入卡位移 + 分相淡入出）
        val morph = updateTransition(targetState = editing, label = "morph")
        val wheelAlpha = morph.animateFloat(
            transitionSpec = {
                // 卡面提前就位（内容落进一张已存在的卡）；退出时后溶解（内容先浮出）
                if (targetState) keyframes { durationMillis = 340; 0f at 60; 1f at 280 }
                else keyframes { durationMillis = 440; 1f at 180; 0f at 380 }
            },
            label = "wheelAlpha",
        ) { if (it) 1f else 0f }
        val wheelScale = morph.animateFloat(
            // 卡面 360ms 内完成放大：末卡落位时卡面已定型，杜绝落点漂移
            transitionSpec = { tween(360, easing = FastOutSlowInEasing) },
            label = "wheelScale",
        ) { if (it) 1f else 0.55f }
        val dispAlpha = morph.animateFloat(
            transitionSpec = {
                // 展示头部随内容一同沉入（早衰减）；浮出时内容抵岸才显形
                if (targetState) keyframes { durationMillis = 360; 1f at 40; 0.35f at 200; 0f at 320 }
                else keyframes { durationMillis = 440; 0f at 280; 0.5f at 370; 1f at 430 }
            },
            label = "dispAlpha",
        ) { if (it) 0f else 1f }
        val dispScale = morph.animateFloat(
            transitionSpec = { tween(460, easing = FastOutSlowInEasing) },
            label = "dispScale",
        ) { if (it) 0.60f else 1f }
        val dispDy = morph.animateDp(
            transitionSpec = { tween(460, easing = FastOutSlowInEasing) },
            label = "dispDy",
        ) { if (it) 0.dp else 0.dp }
        // ★ M24 内容形变进度：任务卡从展示态位形变到卡槽位（内容过渡而来，不重新出现）
        val morphP = morph.animateFloat(
            transitionSpec = { tween(460, easing = FastOutSlowInEasing) },
            label = "morphP",
        ) { if (it) 1f else 0f }
        val density = LocalDensity.current
        val screenH = LocalConfiguration.current.screenHeightDp.dp
        val selDayIdx = days.indexOfFirst { it.date == selDate.format(DateTimeFormatter.ISO_LOCAL_DATE) }
        val selDay = if (selDayIdx in days.indices) days[selDayIdx] else null
        val selBlocks = selDay?.blocks ?: emptyList()
        val selExtent = extentOf(selBlocks)
        val flights = selBlocks.mapNotNull { b -> dispRects[b.id]?.let { b to it } }
        val morphReady = selBlocks.isNotEmpty() && flights.size == selBlocks.size
        // ★ M26 抗卡顿：组合层只订阅 derived 布尔（整轮动画仅重组 2 次）；
        //   连续动画量一律在 graphicsLayer/offset/layout 延迟闭包内取值，逐帧零重组。
        val wheelShow by remember { derivedStateOf { wheelAlpha.value > 0.01f } }
        val dispShow by remember { derivedStateOf { dispAlpha.value > 0.01f } }
        val morphFlight by remember(morphReady) {
            derivedStateOf { morphReady && morphP.value > 0.001f && morphP.value < 0.999f }
        }
        val cardsAlpha: () -> Float = { if (morphReady) 1f - minOf(1f, morphP.value * 12f) else 1f }

        if (wheelShow) {
            // ★ 星期卡片编辑态：七日长圆角卡惯性轮盘（自卡片尺寸浮现）
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = wheelScale.value
                        scaleY = wheelScale.value
                        alpha = wheelAlpha.value
                    }
            ) {
                LazyRow(
                    state = listState,
                    flingBehavior = snap,
                    contentPadding = PaddingValues(horizontal = sidePad),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(days) { index, day ->
                        DayCard(
                            day = day,
                            dateLabel = day.date.substring(5).replace('-', '/'),
                            isToday = day.date == today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            listState = listState,
                            index = index,
                            hideContent = morphFlight && index == selDayIdx,
                            onChange = { newBlocks ->
                                // ★ 事件时刻新鲜读 state（非组合期快照）：连加任务永不吞并
                                days[index] = days[index].copy(blocks = newBlocks)
                                scheduleSave()
                            },
                            // ★ 滑块专用：每次拖拽都在事件时刻基于「最新」块表变换，
                            //   杜绝 pointerInput 旧闭包持有旧 day 快照造成的吞并/跳回。
                            onTransform = { transform ->
                                days[index] = days[index].copy(blocks = transform(days[index].blocks))
                                scheduleSave()
                            },
                        )
                    }
                }
            }
            GlassPill(
                "完成",
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()   // 落到状态栏/挖孔之下，任何机型都按得到
                    .padding(top = 12.dp, start = 14.dp)
                    .graphicsLayer { alpha = wheelAlpha.value },   // 随卡面渐显，杜绝瞬弹
                sound = IosSounds.Cue.Tock,
            ) {
                saveNow()
                syncSelFromWheel()
                editing = false
            }
        }
        if (dispShow) {
            // ★ 日程核心（展示态）：当日任务卡片列表；双指内划时沉入屏幕、缩成卡片内容
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = dispScale.value
                        scaleY = dispScale.value
                        translationY = dispDy.value.toPx()
                        alpha = dispAlpha.value
                    }
            ) {
                DayDisplayList(
                    date = selDate,
                    cardsAlpha = cardsAlpha,
                    onCardBounds = { id, r -> if (!editing) dispRects[id] = r },
                    onShift = { delta ->
                        selDate = selDate.plusDays(delta.toLong()).coerceIn(weekStart, weekStart.plusDays(6))
                    },
                )
            }
        }
        if (morphFlight && selDay != null) {
            // ★ M24 内容形变层：任务卡按 eased 进度从展示态位形变飞入卡槽（同 UI 面，落点像素级一致）
            val e0 = selExtent[0]
            val e1 = selExtent[1]
            val faceLeftPx = with(density) { sidePad.toPx() }
            // ★ 落点以实测内容区高为准（屏高含底部玻璃导航，旧公式落点偏低 = 末段隐性跳变）
            val faceTopPx = (rootH - with(density) { CARD_H.toPx() }) / 2f + with(density) { 50.dp.toPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = minOf(1f, morphP.value * 12f) }
            ) {
                val insetL = with(density) { 28.dp.toPx() }   // 展示卡整体含时间轴列 28dp
                val insetB = with(density) { 14.dp.toPx() }   // 白面底距 14dp
                val frames = blockFrames(flights.map { it.first }, e0, e1, TRACK_H.value, LABEL_ZONE)
                flights.forEachIndexed { fi, (block, start) ->
                    val fr = frames[fi]   // 与 DayCard 同一套排布（含防叠压缩），落点像素级一致
                    val endL = faceLeftPx + with(density) { 30.dp.toPx() }
                    val endT = faceTopPx + with(density) { fr[0].dp.toPx() }
                    val endW = with(density) { (CARD_W - 42.dp).toPx() }
                    val endH = with(density) { fr[1].dp.toPx() }
                    // 起点 = 展示态白面（剔除时间轴列与底距）→ 手over像素级衔接
                    val x0 = start.left - rootWinOff.x + insetL
                    val y0 = start.top - rootWinOff.y
                    val w0 = start.width - insetL
                    val h0 = start.height - insetB
                    // 逐卡微错峰（12%）：自然先后感；末卡收尾时全部归位
                    // ★ M26 抗卡顿：位置/尺寸在 offset/layout 延迟闭包内按 State 计算，逐帧零重组
                    val stride = 0.12f * (flights.size - 1)
                    BlockCardFace(
                        block = block,
                        date = selDay.date,
                        modifier = Modifier
                            .offset {
                                val p = ((morphP.value * (1f + stride)) - 0.12f * fi).coerceIn(0f, 1f)
                                IntOffset(
                                    (x0 + (endL - x0) * p).roundToInt(),
                                    (y0 + (endT - y0) * p).roundToInt(),
                                )
                            }
                            .layout { measurable, _ ->
                                val p = ((morphP.value * (1f + stride)) - 0.12f * fi).coerceIn(0f, 1f)
                                val w = (w0 + (endW - w0) * p).roundToInt().coerceAtLeast(1)
                                val h = (h0 + (endH - h0) * p).roundToInt().coerceAtLeast(1)
                                val pl = measurable.measure(Constraints.fixed(w, h))
                                layout(w, h) { pl.place(0, 0) }
                            },
                    )
                }
            }
        }
        // 顶部毛玻璃胶囊按钮（点「导入」弹入口卡：粘贴剪贴板 / 选择文件，解析后进预览）
        GlassPill("导入", Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 14.dp), onClick = { showImportSheet = true })
        if (showImportSheet) {
            ImportEntrySheet(
                onDismiss = { showImportSheet = false },
                onClipboard = {
                    showImportSheet = false
                    importVm.parse(clipboard.getText()?.text)
                    onOpenPreview()
                },
                onFile = {
                    showImportSheet = false
                    importFilePicker.launch(
                        arrayOf(
                            "application/json",
                            "text/plain",
                            "application/octet-stream",
                            "application/*",
                        )
                    )
                },
            )
        }
        // 底部操作提示（展示态兼作点按兜底入口）
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = (1f - wheelAlpha.value).coerceIn(0f, 1f) }   // 编辑态隐提示条：纵向空间让给放大后的卡片
                .padding(bottom = 118.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x66FFFFFF))
                .border(1.dp, Color(0x8CFFFFFF), RoundedCornerShape(50))
                .then(
                    // 编辑态整段移除输入节点：alpha 隐身的 clickable 仍占命中链，会吞下方「＋」
                    if (!editing) {
                        Modifier.clickable {
                            IosSounds.play(IosSounds.Cue.Pop)
                            editing = true
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Crossfade(targetState = editing, animationSpec = tween(220), label = "hint") { edit ->
                Text(
                    if (edit) "拖动滑块调时间 · 点块编辑（名/时/色/删）· 长按块速删 · 左右滑换天"
                    else "双指向内划（或点这里）· 编辑日程",
                    color = Color(0xA05A5468),
                    fontSize = 11.sp,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

// ── 展示态：任务卡片核心（原「日程」日列表迁入）────────────

@Composable
private fun DayDisplayList(
    date: LocalDate,
    cardsAlpha: () -> Float = { 1f },
    onCardBounds: (String, Rect) -> Unit = { _, _ -> },
    onShift: (Int) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as PastelleApp).repository }
    val tasks by repo.observeTasks(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        .collectAsState(initial = emptyList())
    val todayD = remember { LocalDate.now() }

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),   // 头部让出状态栏/挖孔（背景仍由 GlassBackdrop 通顶，不露白）
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 26.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 110.dp),   // 末卡可滑出悬浮胶囊区
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 换日头部：‹ 周三 09/23 今天 ›
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassPill("‹") { onShift(-1) }
                Spacer(Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        DAY_LABELS[date.dayOfWeek.value % 7],
                        color = Color(0xFF3D3450),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                    )
                    Text(
                        "%02d/%02d".format(date.monthValue, date.dayOfMonth),
                        color = Color(0xFF8A8494),
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                    )
                    if (date == todayD) {
                        Box(
                            Modifier
                                .padding(top = 5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x33D98BA5))
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("今天", color = Color(0xFFC0678C), fontSize = 8.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                GlassPill("›") { onShift(1) }
            }

            Spacer(Modifier.height(20.dp))
            if (tasks.isEmpty()) {
                Text("这一天还没有安排", color = InkSoft, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("双指向内划 · 开始编辑", color = InkSoft.copy(alpha = 0.7f), fontSize = 11.sp)
            } else {
                tasks.sortedBy { it.startMinute }.forEachIndexed { index, task ->
                    Box(
                        Modifier
                            .graphicsLayer { alpha = cardsAlpha() }
                            .onGloballyPositioned { onCardBounds(task.id, it.boundsInWindow()) }
                    ) {
                        TaskTimelineCard(
                            startLabel = TimeUtils.formatHhMm(task.startMinute),
                            title = task.title,
                            durationLabel = TimeUtils.minutesLabel(task.durationMin),
                            themeColor = (TaskColor.fromKey(task.color) ?: TaskColor.Pink).mainColor(),
                            index = index,
                            stage = task.stage,
                            location = task.location,
                            note = task.note,
                            trailing = taskStatusLabel(task),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/** 任务状态标签：与今日页 / 桌面小组件共用 [TaskDisplayStatus] 单一口径 */
@Composable
private fun taskStatusLabel(task: TaskEntity): String = when (task.displayStatus()) {
    TaskDisplayStatus.COMPLETED -> stringResource(R.string.status_completed)
    TaskDisplayStatus.UPCOMING -> stringResource(R.string.status_upcoming)
    TaskDisplayStatus.PREP -> stringResource(R.string.status_prep)
    TaskDisplayStatus.ONGOING -> stringResource(R.string.status_ongoing)
    TaskDisplayStatus.MISSED -> stringResource(R.string.status_missed)
}

/** 星期卡片内任务块的状态标（与展示态一致：已完成/待开始/进行中/已错过） */
@Composable
private fun blockStatus(date: String, b: WheelBlock): String {
    if (b.done) return stringResource(R.string.status_completed)
    val nowMs = System.currentTimeMillis()
    val t0 = TimeUtils.epochMs(date, b.startMin)
    val prepStart = t0 - PastelleConstants.PREP_SECONDS * 1000L
    val end = t0 + (b.endMin - b.startMin) * 60_000L
    return when {
        nowMs < prepStart -> stringResource(R.string.status_upcoming)
        nowMs < t0 -> stringResource(R.string.status_prep)
        nowMs < end -> stringResource(R.string.status_ongoing)
        else -> stringResource(R.string.status_missed)
    }
}

@Composable
private fun GlassPill(text: String, modifier: Modifier = Modifier, sound: IosSounds.Cue = IosSounds.Cue.Tink, onClick: () -> Unit) {
    Box(
        modifier
            .shadow(6.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(Color(0x70FFFFFF))
            .border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(50))
            .clickable {
                IosSounds.play(sound)
                onClick()
            }
            .padding(horizontal = 17.dp, vertical = 10.dp)   // 命中区加高至 ≥44dp
    ) {
        Text(text, color = Color(0xFF5A5468), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
    }
}

private fun taskOf(date: String, b: WheelBlock, order: Int): TaskEntity = TaskEntity(
    id = b.id,
    date = date,
    title = b.title,
    startMinute = b.startMin,
    durationMin = (b.endMin - b.startMin).coerceAtLeast(15),
    stage = null,
    location = null,
    note = null,
    color = PALETTE_NAMES[b.colorIdx % PALETTE_NAMES.size],
    orderIndex = order,
    state = if (b.done) TaskState.COMPLETED else TaskState.WAITING,
    phaseEndEpochMs = 0L,
    pomodoroCount = 0,
    startedAt = null,
    completedAt = null,
    elapsedMinOnComplete = null,
    timeVariancePct = null,
    completionPct = null,
    xpEarned = null,
)

/**
 * ★ 块排布（M27）：占比排布 + 最小高度/防重叠兜底。
 * 块多到占比高度互相压盖（旧 34dp 最小高会叠住前块 = 任务「消失」）时，
 * 自动改等分压缩排布 —— 任何数量的任务都完整可见。返回与 blocks 同索引的 [top, height]（卡内 dp）。
 */
private fun blockFrames(
    blocks: List<WheelBlock>,
    e0: Int,
    e1: Int,
    trackH: Float,
    labelZone: Float,
): List<FloatArray> {
    val n = blocks.size
    if (n == 0) return emptyList()
    val minH = 34f
    val gap = 3f
    val sorted = blocks.indices.sortedBy { blocks[it].startMin }
    val ideals = sorted.map { bi ->
        val t = yNorm(blocks[bi].startMin, e0, e1) * trackH
        val h = (yNorm(blocks[bi].endMin, e0, e1) - yNorm(blocks[bi].startMin, e0, e1)) * trackH
        t to h
    }
    val fits = ideals.all { it.second >= minH } && n * minH + (n - 1) * gap <= trackH
    val hUni = (trackH - (n - 1) * gap) / n
    val out = Array(n) { FloatArray(2) }
    sorted.forEachIndexed { i, bi ->
        if (fits) {
            out[bi][0] = labelZone + ideals[i].first
            out[bi][1] = ideals[i].second
        } else {
            out[bi][0] = labelZone + i * (hUni + gap)
            out[bi][1] = hUni
        }
    }
    return out.toList()
}

private fun coalesce(blocks: List<WheelBlock>): List<WheelBlock> {
    val out = mutableListOf<WheelBlock>()
    blocks.sortedBy { it.startMin }.forEach { b ->
        val last = out.lastOrNull()
        when {
            last == null -> out.add(b)
            b.startMin < last.endMin -> out[out.size - 1] = last.copy(endMin = maxOf(last.endMin, b.endMin))
            else -> out.add(b)
        }
    }
    return out   // 颜色跟随用户自定义（不再按位重排抹掉）
}

// ── 毛玻璃背景（径向渐变光斑：无 blur 裁切硬边）────────────

@Composable
private fun GlassBackdrop() {
    // ★ M28.18：底色由不透明粉紫渐变换成与全局一致的半透明调和层，
    //   让下层视频能透出来；三枚彩色光斑保留为极淡点缀，避免压住视频。
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawVideoScrim() }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x33F7C6D9), Color(0x00F7C6D9)),
                    center = Offset(w * 0.16f, h * 0.24f),
                    radius = w * 0.62f,
                ),
                radius = w * 0.62f,
                center = Offset(w * 0.16f, h * 0.24f),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x2BBBD4F2), Color(0x00BBD4F2)),
                    center = Offset(w * 0.88f, h * 0.62f),
                    radius = w * 0.7f,
                ),
                radius = w * 0.7f,
                center = Offset(w * 0.88f, h * 0.62f),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x24C8ECD8), Color(0x00C8ECD8)),
                    center = Offset(w * 0.5f, h * 0.93f),
                    radius = w * 0.55f,
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.5f, h * 0.93f),
            )
        }
    }
}

// ── 日卡（轮盘项）────────────────────────────────────────

@Composable
private fun DayCard(
    day: WheelDay,
    dateLabel: String,
    isToday: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    index: Int,
    onChange: (List<WheelBlock>) -> Unit,
    onTransform: ((List<WheelBlock>) -> List<WheelBlock>) -> Unit,
    hideContent: Boolean = false,
) {
    // 翻滚错位修复：瞬离可见表时保持最后距离（旧逻辑跳 1f → 缩放/透明度瞬变＝卡片跳位）
    val lastDist = remember { androidx.compose.runtime.mutableStateOf(1f) }
    val dist by remember {
        derivedStateOf {
            val vp = listState.layoutInfo
            val info = vp.visibleItemsInfo.firstOrNull { it.index == index }
                ?: return@derivedStateOf lastDist.value
            val center = (vp.viewportStartOffset + vp.viewportEndOffset) / 2f
            val itemCenter = info.offset + info.size / 2f
            val d = (abs(itemCenter - center) / info.size).coerceIn(0f, 1f)
            lastDist.value = d
            d
        }
    }
    val active = dist < 0.5f
    var editing by remember { mutableStateOf<Int?>(null) }
    var confirmingDelete by remember { mutableStateOf<Int?>(null) }
    val (CARD_W, CARD_H, TRACK_H, LABEL_ZONE) = cardGeom()
    val ext = extentOf(day.blocks)
    val e0 = ext[0]
    val e1 = ext[1]

    // 外层：滑动缩放 + 右侧筹码溢出空间
    // ★ 翻滚居中修复：item 宽度必须严格等于卡面宽（= content area 宽），
    //   否则 snap 目标（item 中心 / item 左沿二义）会让卡面偏 48dp。
    //   筹码区改由内层 requiredWidth 溢出绘制，不参与 item 测量宽度。
    Box(
        Modifier
            .width(CARD_W)
            .fillMaxHeight()
            .graphicsLayer {
                val s = 1f - dist * 0.24f
                scaleX = s
                scaleY = s
                alpha = 1f - dist * 0.42f
            },
    ) {
        // ★ 卡体容器（282dp 坐标系：左 186dp 卡面 + 右 96dp 筹码区）
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(y = 50.dp)   // 与 faceTopPx 同公式（内容区实高居中 + 50dp 避让顶部按钮）
                // ★ 偏左 48dp 的病根：requiredWidth 比 item 宽 96dp 时，Compose 会把超宽
                //   内容「自动居中」，等价于把卡面整体左移 96/2 = 48dp。改用显式 layout：
                //   仍按「卡面 + 筹码区」宽度测量，但强制从 x=0 起绘，向父级只报告卡面宽。
                .layout { measurable, constraints ->
                    val fullW = (CARD_W + 96.dp).roundToPx()
                    val p = measurable.measure(constraints.copy(minWidth = fullW, maxWidth = fullW))
                    layout(CARD_W.roundToPx(), p.height) { p.place(0, 0) }
                }
                .height(CARD_H)
        ) {
            // ★ 去大框（M28.11）：不再套整卡白底——标题与任务块直接坐在流动背景上，单层卡片
            // 顶部：星期 + 日期 + 今天徽标（字阶收紧 + 粉彩徽标）
            Text(
                day.label,
                color = Color(0xFF3D3450),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = 15.dp)
                    .width(CARD_W),
            )
            Text(
                dateLabel,
                color = Color(0xFF8A8494),
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = 43.dp)
                    .width(CARD_W),
            )
            if (isToday) {
                Box(
                    Modifier
                        .offset(x = CARD_W / 2 - 17.dp, y = 61.dp)
                        .size(34.dp, 16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x33D98BA5))
                ) {
                    Text(
                        "今天",
                        color = Color(0xFFC0678C),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(34.dp),
                    )
                }
            }
            // 小时刻度（易用性：一眼定位一天中的位置）
            HourTicks(e0, e1)
            // 空态提示
            if (day.blocks.isEmpty()) {
                Text(
                    "空空如也\n点下方 ＋ 加一块任务",
                    color = Color(0x8C7A7390),
                    fontSize = 11.sp,
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(CARD_W),
                )
            }
            // 任务块（分割线之间的区域）：配色底 + 左彩条；双击编辑
            val frames = blockFrames(day.blocks, e0, e1, TRACK_H.value, LABEL_ZONE)
            day.blocks.forEachIndexed { bi, block ->
                val top = frames[bi][0]
                val h = frames[bi][1]
                // ★ 任务 UI 与展示态一致（BlockCardFace）；形变飞行期由形变层代绘
                if (!hideContent) {
                    BlockCardFace(
                        block = block,
                        date = day.date,
                        compact = h < 46f,   // ★ 矮块切紧凑单行，任务内文永不消失
                        modifier = Modifier
                            .offset(x = 30.dp, y = top.dp)
                            .size(CARD_W - 42.dp, h.dp)
                            .pointerInput(block.id, active) {
                                detectTapGestures(
                                    onTap = {
                                        if (active) {
                                            IosSounds.play(IosSounds.Cue.Pop)
                                            editing = bi
                                        }
                                    },
                                    onLongPress = {
                                        if (active) {
                                            IosSounds.play(IosSounds.Cue.Thunk)
                                            confirmingDelete = bi
                                        }
                                    },
                                )
                            },
                    )
                }
            }
            // 分割滑块们（仅居中卡渲染：避免邻卡溢出串扰）
            if (active) for (bi in 0 until day.blocks.size - 1) {
                val boundary = day.blocks[bi].endMin
                val top = frames[bi][0] + frames[bi][1]
                DividerRow(
                    top = top,
                    min = boundary,
                    spanMin = e1 - e0,
                    active = active,
                    // 分隔线身份（相邻两块 id）：只在块增删/换日时重置拖拽态，
                    // 绝不随外部 min 回写而重置（旧 remember(min) 会把累计位移清零＝拖不动）。
                    dividerKey = day.blocks[bi].id + "|" + day.blocks[bi + 1].id,
                    onDragTo = { newMin ->
                        // 事件时刻基于最新块表变换：连续拖拽每帧都不丢上一帧结果
                        onTransform { cur ->
                            if (bi < cur.size - 1) {
                                val lo = cur[bi].startMin + 15
                                val hi = cur[bi + 1].endMin - 15
                                if (lo <= hi) {
                                    val t = newMin.coerceIn(lo, hi)
                                    val nb = cur.toMutableList()
                                    nb[bi] = nb[bi].copy(endMin = t)
                                    nb[bi + 1] = nb[bi + 1].copy(startMin = t)
                                    nb
                                } else cur
                            } else cur
                        }
                    },
                )
            }
            // ＋ 增块（卡底居中胶囊）
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = CARD_W / 2 - 22.dp, y = (-13).dp)
                    .size(44.dp)
                    .shadow(10.dp, CircleShape, clip = false, ambientColor = Color(0x33B6A6E0), spotColor = Color(0x26F3B6C4))
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFFBFD), Color(0xFFF0EAF8))))
                    .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                    .pointerInput(day, active) {   // ★ 键=整个 day：连点「＋」期间闭包永远新鲜，杜绝旧快照吞并
                        detectTapGestures(onTap = {
                            if (!active) return@detectTapGestures
                            IosSounds.play(IosSounds.Cue.Tink)
                            // ★ 修「＋」吞任务：①取真正最晚块的结束时刻（非列表序末位）②不再顶旧的 21:30
                            //   硬上限（新块与旧块重叠会被 coalesce 合并 = 任务凭空消失）
                            val maxEnd = day.blocks.maxOfOrNull { it.endMin } ?: 540
                            val free = 1440 - maxEnd
                            if (free >= 15) {
                                val dur = minOf(60, free)
                                onChange(
                                    day.blocks + WheelBlock(
                                        id = UUID.randomUUID().toString(),
                                        title = "新任务",
                                        startMin = maxEnd,
                                        endMin = maxEnd + dur,
                                        colorIdx = (day.blocks.maxOfOrNull { it.colorIdx } ?: -1) + 1,   // 接续调色板下一色
                                    )
                                )
                            }
                        })
                    }
            ) {
                Text(
                    "＋",
                    color = Color(0x8C5A5468),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(44.dp),
                )
            }
            // 双击编辑对话框
            editing?.let { bi ->
                BlockEditDialog(
                    block = day.blocks[bi],
                    onConfirm = { title, s, e, colorIdx ->
                        val nb = day.blocks.toMutableList()
                        val lo = if (bi > 0) nb[bi - 1].startMin + 15 else TRACK_TOP
                        val hi = if (bi < nb.size - 1) nb[bi + 1].endMin - 15 else TRACK_TOP + TRACK_SPAN
                        val ss = s.coerceIn(lo, hi - 15)
                        val ee = e.coerceIn(ss + 15, hi)
                        nb[bi] = nb[bi].copy(title = title, startMin = ss, endMin = ee, colorIdx = colorIdx)
                        if (bi > 0) nb[bi - 1] = nb[bi - 1].copy(endMin = ss)
                        if (bi < nb.size - 1) nb[bi + 1] = nb[bi + 1].copy(startMin = ee)
                        onChange(nb)
                        editing = null
                    },
                    onDelete = {
                        val nb = day.blocks.toMutableList()
                        nb.removeAt(bi)
                        onChange(nb)   // 保留其余块的自定义颜色
                        editing = null
                    },
                    onDismiss = { editing = null },
                )
            }
            // 长按删除确认（与「＋」对偶：块可增可减）
            confirmingDelete?.let { di ->
                AlertDialog(
                    onDismissRequest = { confirmingDelete = null },
                    containerColor = Color(0xF7FFFFFF),
                    title = { Text("删除任务块", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                    text = {
                        Text(
                            "删除「" + (day.blocks.getOrNull(di)?.title ?: "") + "」？此操作不可恢复。",
                            color = InkSoft,
                            fontSize = 13.sp,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            IosSounds.play(IosSounds.Cue.Thunk)
                            val nb = day.blocks.toMutableList()
                            if (di in nb.indices) nb.removeAt(di)
                            onChange(nb)
                            confirmingDelete = null
                        }) { Text("删除", color = Color(0xFFC75B6A)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmingDelete = null }) { Text("取消", color = InkSoft) }
                    },
                )
            }
        }
    }
}

// ── 任务卡面（= TaskCardSurface 同源：与展示态逐像素一致，形变前后字体不变）────
@Composable
private fun BlockCardFace(
    block: WheelBlock,
    date: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    TaskCardSurface(
        startLabel = fmt(block.startMin),
        title = block.title,
        durationLabel = "%d 分钟".format(block.endMin - block.startMin),
        themeColor = (TaskColor.fromKey(PALETTE_NAMES[block.colorIdx % PALETTE_NAMES.size]) ?: TaskColor.Pink).mainColor(),
        trailing = blockStatus(date, block),
        modifier = modifier,
        compact = compact,
    )
}

// ── 小时刻度 ──────────────────────────────────────────────

@Composable
private fun HourTicks(e0: Int, e1: Int) {
    val (CARD_W, CARD_H, TRACK_H, LABEL_ZONE) = cardGeom()
    val span = e1 - e0
    val step = when {
        span <= 240 -> 60
        span <= 540 -> 120
        else -> 180
    }
    val ticks = mutableListOf<Int>()
    var t = ((e0 + step - 1) / step) * step
    while (t <= e1) {
        ticks.add(t)
        t += step
    }
    val majors = if (ticks.size <= 3) ticks else listOf(ticks.first(), ticks[ticks.size / 2], ticks.last())
    Canvas(
        Modifier
            .offset(y = LABEL_ZONE.dp)
            .height(TRACK_H)
            .width(CARD_W)
    ) {
        ticks.forEach { m ->
            val y = yNorm(m, e0, e1) * size.height
            val major = majors.contains(m)
            drawCircle(
                color = InkSoft.copy(alpha = if (major) 0.32f else 0.16f),
                radius = if (major) 1.5.dp.toPx() else 1.dp.toPx(),
                center = Offset(if (major) 12.dp.toPx() else 10.dp.toPx(), y),
            )
        }
    }
    for (m in majors) {
        val y = LABEL_ZONE + yNorm(m, e0, e1) * TRACK_H.value
        Text(
            "%02d".format(m / 60),
            color = Color(0x8C7A7390),
            fontSize = 8.5.sp,
            letterSpacing = 0.3.sp,
            modifier = Modifier.offset(x = 4.dp, y = (y - 5).dp),
        )
    }
}

// ── 分割滑块行：贯穿线 + 胶囊手柄 + 虚线 + 时间筹码 ───────

@Composable
private fun DividerRow(
    top: Float,
    min: Int,
    spanMin: Int,
    active: Boolean,
    dividerKey: String,
    onDragTo: (Int) -> Unit,
) {
    val (CARD_W, CARD_H, TRACK_H, LABEL_ZONE) = cardGeom()
    val trackPx = with(LocalDensity.current) { TRACK_H.toPx() }
    // ★ 拖不动的病根：拖拽态用 remember(min) 绑定外部值，onDragTo 每帧回写 min 时
    //   remember 立刻重建，dragFromMin 被改写、dragAcc 归零 ⇒ 累计位移丢失、手柄回弹。
    //   现改为只绑定「分隔线身份」：外部 min 怎么变都不重置本轮手势。
    var dragging by remember(dividerKey) { mutableStateOf(false) }
    var dragFromMin by remember(dividerKey) { mutableStateOf(min) }
    var dragAccPx by remember(dividerKey) { mutableStateOf(0f) }
    // pointerInput 只装一次；UpdatedState 让闭包始终读到最新 min/span/回调，
    // 杜绝旧 day 快照吞并拖拽结果（与「＋」增块同一治法）。
    val curMin = rememberUpdatedState(min)
    val curSpan = rememberUpdatedState(spanMin)
    val curTrackPx = rememberUpdatedState(trackPx)
    val curActive = rememberUpdatedState(active)
    val curOnDragTo = rememberUpdatedState(onDragTo)
    fun targetOf(acc: Float): Int =
        (dragFromMin + (acc / curTrackPx.value * curSpan.value).roundToInt())
            .coerceIn(TRACK_TOP, TRACK_TOP + TRACK_SPAN)
    val shown = if (dragging) targetOf(dragAccPx) else min

    Box(
        Modifier
            .offset(y = top.dp - 18.dp)
            .requiredWidth(CARD_W + 96.dp)
            .height(36.dp)   // 仅视觉带；命中收进下方手柄（放行块的点按/长按）
    ) {
        Canvas(Modifier.matchParentSize()) {
            val lineY = size.height / 2f
            // 发丝线（12% 墨雾，不再拉虚线）
            drawLine(
                color = Color(0x221F1B2E),
                start = Offset(22.dp.toPx(), lineY),
                end = Offset(CARD_W.toPx() - 18.dp.toPx(), lineY),
                strokeWidth = 1.dp.toPx(),
            )
        }
        // 内联时间手柄：●● 09:58 一体奶片（随拖实时刷新：上划变小/下滑变大）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-48).dp)
                .shadow(5.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.94f))
                .border(1.dp, Color(0x241F1B2E), RoundedCornerShape(50))
                .pointerInput(dividerKey) {   // 拖拽只在手柄奶片上；块面其余区域放行点按
                    detectDragGestures(
                        onDragStart = { _ ->
                            dragFromMin = curMin.value   // 起手基线＝当前真实分割时刻
                            dragAccPx = 0f
                            dragging = true
                        },
                        onDrag = { change, amount ->
                            if (curActive.value) {
                                change.consume()          // 吃掉纵向位移，不与轮盘横向滚动抢手势
                                dragAccPx += amount.y
                                curOnDragTo.value(targetOf(dragAccPx))
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            dragAccPx = 0f
                            IosSounds.play(IosSounds.Cue.Tock)
                        },
                        onDragCancel = {
                            dragging = false
                            dragAccPx = 0f
                        },
                    )
                }
                .padding(horizontal = 9.dp, vertical = 4.5.dp)
        ) {
            Box(Modifier.size(3.dp).clip(CircleShape).background(Color(0x593D3450)))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.size(3.dp).clip(CircleShape).background(Color(0x593D3450)))
            Spacer(Modifier.width(6.dp))
            Text(
                fmt(shown),
                color = Color(0xFF4A4358),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            )
        }
    }
}

// ── 双击编辑对话框 ────────────────────────────────────────

@Composable
private fun BlockEditDialog(
    block: WheelBlock,
    onConfirm: (String, Int, Int, Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(block.id) { mutableStateOf(block.title) }
    var start by remember(block.id) { mutableStateOf(fmt(block.startMin)) }
    var end by remember(block.id) { mutableStateOf(fmt(block.endMin)) }
    var colorSel by remember(block.id) { mutableStateOf(block.colorIdx % PALETTE_NAMES.size) }
    var confirmDelete by remember(block.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF7FFFFFF),
        title = { Text("编辑任务", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                FieldRow("名称", title) { title = it }
                FieldRow("开始", start) { start = it }
                FieldRow("结束", end) { end = it }
                // 自定义颜色（与旧日程编辑器同款彩色圆片）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text("颜色", color = InkSoft, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                    PALETTE_NAMES.forEachIndexed { i, key ->
                        val sel = colorSel % PALETTE_NAMES.size == i
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background((TaskColor.fromKey(key) ?: TaskColor.Pink).mainColor())
                                .border(
                                    if (sel) 2.dp else 1.dp,
                                    if (sel) Ink else Color(0x1A000000),
                                    CircleShape,
                                )
                                .clickable {
                                    IosSounds.play(IosSounds.Cue.Tink)
                                    colorSel = i
                                }
                        )
                    }
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除任务块", color = Color(0xFFC75B6A), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                IosSounds.play(IosSounds.Cue.Ack)
                val s = parseMin(start) ?: block.startMin
                val e = parseMin(end) ?: block.endMin
                onConfirm(title.ifBlank { "新任务" }, s, e, colorSel)
            }) { Text("确定", color = Ink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = InkSoft) }
        },
    )

    // ★ 与「长按删除」同一套确认：以前编辑弹窗里点删除直接生效，误触无法挽回
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color(0xF7FFFFFF),
            title = { Text("删除任务块", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    "删除「${block.title}」？此操作不可恢复。",
                    color = InkSoft,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    IosSounds.play(IosSounds.Cue.Thunk)
                    confirmDelete = false
                    onDelete()
                }) { Text("删除", color = Color(0xFFC75B6A)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消", color = InkSoft) }
            },
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, onValue: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        Text(label, color = InkSoft, fontSize = 13.sp, modifier = Modifier.width(44.dp))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 14.sp),
            cursorBrush = SolidColor(Ink),
            modifier = Modifier
                .width(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x14000000))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

private fun parseMin(s: String): Int? {
    val parts = s.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return (h * 60 + m).coerceIn(TRACK_TOP, TRACK_TOP + TRACK_SPAN)
}
