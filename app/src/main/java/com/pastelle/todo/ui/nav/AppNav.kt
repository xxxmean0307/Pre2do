package com.pastelle.todo.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pastelle.todo.PastelleApp
import com.pastelle.todo.ui.components.PastelBackground
import com.pastelle.todo.ui.components.VideoBackground
import com.pastelle.todo.ui.home.HomeScreen
import com.pastelle.todo.ui.execution.ExecutionScreen
import com.pastelle.todo.ui.importflow.ImportPreviewScreen
import com.pastelle.todo.ui.importflow.rememberImportViewModel
import com.pastelle.todo.ui.more.MoreScreen
import com.pastelle.todo.ui.rememberTimingEngine
import com.pastelle.todo.ui.reward.RewardOverlay
import com.pastelle.todo.ui.schedule.ScheduleEditorScreen
import com.pastelle.todo.ui.schedule.ScheduleScreen
import com.pastelle.todo.ui.stats.StatsScreen
import com.pastelle.todo.ui.theme.Motion
import com.pastelle.todo.ui.weekwheel.WeekWheelScreen
import kotlinx.coroutines.flow.first

private const val ROUTE_IMPORT_PREVIEW = "import_preview"
private const val ROUTE_SCHEDULE_CALENDAR = "schedule_calendar"
private const val ROUTE_EXECUTION = "execution"
private const val ROUTE_SCHEDULE_EDITOR = "schedule_editor"

/**
 * 单 Activity 架构的导航骨架：
 * 底部四 Tab + Navigation Compose，页面切换淡入 + 上移 8dp（规格 3.6）。
 * 悬浮毛玻璃胶囊底栏：内容全高铺底、画面从胶囊下穿过并在玻璃下透出。
 * pendingShareText：从系统分享菜单进入的日程文本（直接进入导入预览，规格 6.2）。
 */
@Composable
fun AppNav(
    pendingShareText: String? = null,
    onShareConsumed: () -> Unit = {},
    pendingRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val importVm = rememberImportViewModel()
    val engine = rememberTimingEngine()
    val risePx = with(LocalDensity.current) { 8.dp.roundToPx() }

    // 奖励动画（规格 7.3）：完成落库后播放；进程被杀可在重开后补播一次（9.6）
    var reward by remember { mutableStateOf<com.pastelle.todo.timing.TimingEvent.Completed?>(null) }
    // 星期卡片编辑态：该界面隐藏底部胶囊（不遮挡「＋」）
    var wheelCardMode by remember { mutableStateOf(false) }
    // ★ 外部请求「直接进编辑态」（今日页「安排今天」/ 小组件「去安排」）
    var wheelEditRequest by remember { mutableStateOf(false) }
    // ★ 离开「日程」页时强制复位编辑态标记：编辑态被系统返回键/导航带走时，
    //   WeekWheelScreen 已销毁、不会再回调 onCardModeChange(false)，
    //   残留 true 会让今日页的底栏一直消失（页面内容被压到屏幕底边）。
    LaunchedEffect(currentRoute) {
        if (currentRoute != TabItem.Schedule.route) wheelCardMode = false
    }
    LaunchedEffect(Unit) {
        val pending = engine.pendingCelebration()
        if (pending != null) {
            engine.ackCelebration()
            val parts = pending.split("|")
            if (parts.size == 4) {
                reward = com.pastelle.todo.timing.TimingEvent.Completed(
                    taskId = parts[0],
                    title = "",
                    gearPct = parts[1].toIntOrNull() ?: 60,
                    xp = parts[2].toIntOrNull() ?: 0,
                    streak = parts[3].toIntOrNull() ?: 0,
                    variancePct = 0,
                )
            }
        }
    }

    // 从分享进入：解析文本并直接进入导入预览页
    LaunchedEffect(pendingShareText) {
        if (pendingShareText != null) {
            importVm.parse(pendingShareText)
            onShareConsumed()
            navController.navigate(ROUTE_IMPORT_PREVIEW)
        }
    }

    // 桌面小组件点击直达：执行页 / 今日日程编辑态
    LaunchedEffect(pendingRoute) {
        when (pendingRoute) {
            com.pastelle.todo.MainActivity.ROUTE_EXECUTION ->
                navController.navigate(ROUTE_EXECUTION) { launchSingleTop = true }
            com.pastelle.todo.MainActivity.ROUTE_SCHEDULE_EDIT -> {
                wheelEditRequest = true
                navController.navigate(TabItem.Schedule.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = false
                }
            }
        }
        if (pendingRoute != null) onRouteConsumed()
    }

    Scaffold(
        containerColor = Color.Transparent,
    ) { _ ->
        // 静态淡紫渐变只是「视频未就绪 / 解码失败」时的兜底底色
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFF4F0F9), Color(0xFFF2EDF6)))),
        ) {
            // ★ 全局唯一视频背景：位于 NavHost 之外，所有页面共享同一个解码器，
            //   切页不重启、不黑闪；各屏的 PastelBackground 调和层负责压淡保证可读性。
            VideoBackground()
            // 内容全高铺底：画面延伸到悬浮胶囊之下（透过毛玻璃隐约可见）；顶部让位由各屏自理
            NavHost(
            navController = navController,
            startDestination = TabItem.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(tween(Motion.TweenMedMs)) +
                    slideInVertically(tween(Motion.TweenMedMs)) { risePx }
            },
            exitTransition = { fadeOut(tween(Motion.TweenFastMs)) },
            popEnterTransition = {
                fadeIn(tween(Motion.TweenMedMs)) +
                    slideInVertically(tween(Motion.TweenMedMs)) { risePx }
            },
            popExitTransition = { fadeOut(tween(Motion.TweenFastMs)) },
        ) {
            composable(TabItem.Home.route) {
                HomeScreen(
                    onOpenSchedule = {
                        navController.navigate(TabItem.Schedule.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenTask = { navController.navigate(ROUTE_EXECUTION) },
                    onAddTask = {
                        // 冷启动最大的断点：今日页没有任何新建入口。这里一步直达「今天」的编辑态。
                        wheelEditRequest = true
                        navController.navigate(TabItem.Schedule.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }
            composable(TabItem.Schedule.route) {
                WeekWheelScreen(
                    onOpenPreview = { navController.navigate(ROUTE_IMPORT_PREVIEW) },
                    onCardModeChange = { wheelCardMode = it },
                    editRequest = wheelEditRequest,
                    onEditRequestHandled = { wheelEditRequest = false },
                )
            }
            // 旧日程视图（情绪日历 + 内建编辑器），经轮盘左上「日历」进入（系统返回键退出）
            composable(ROUTE_SCHEDULE_CALENDAR) {
                ScheduleScreen(
                    vm = importVm,
                    onOpenPreview = { navController.navigate(ROUTE_IMPORT_PREVIEW) },
                    onOpenEditor = { date ->
                        navController.navigate(
                            if (date != null) "$ROUTE_SCHEDULE_EDITOR?date=$date" else ROUTE_SCHEDULE_EDITOR,
                        )
                    },
                )
            }
            composable(TabItem.Stats.route) { StatsScreen() }
            composable(TabItem.More.route) { MoreScreen() }
            // 任务执行页（全屏，无底部栏；规格 4.2）
            composable(ROUTE_EXECUTION) {
                ExecutionScreen(
                    onBack = { navController.popBackStack() },
                    onCompleted = { ev ->
                        // 落库成功 → 300ms 级回退页面 → 播放奖励动画（规格 7.3）
                        navController.popBackStack()
                        reward = ev
                    },
                )
            }
            composable(
                route = "$ROUTE_SCHEDULE_EDITOR?date={date}",
                arguments = listOf(navArgument("date") { nullable = true }),
            ) { entry ->
                ScheduleEditorScreen(
                    editDate = entry.arguments?.getString("date"),
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_IMPORT_PREVIEW) {
                ImportPreviewScreen(
                    vm = importVm,
                    onBack = {
                        importVm.reset()
                        navController.popBackStack()
                    },
                    onImported = {
                        importVm.reset()
                        navController.popBackStack()
                        navController.navigate(TabItem.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            }
            // 悬浮长椭圆胶囊底栏（毛玻璃：画面从胶囊下穿过并透出；内容仍可点按穿行）
            if (TabItem.entries.any { it.route == currentRoute } && !wheelCardMode) {
                BottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    currentRoute = currentRoute,
                    onSelect = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            // 奖励动画浮层（光球飞向 TopProgressBar + 彩纸 + 横幅）
            RewardOverlay(
                reward = reward,
                onFinished = { reward = null },
            )
        }
    }
}
