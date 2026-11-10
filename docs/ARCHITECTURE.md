# 架构说明

## 分层

```
┌─────────────────────────────────────────────────┐
│ ui/  Compose 页面（home / weekwheel / execution   │
│      / stats / more / importflow / nav / theme）  │
│      组件：HeroTaskCard、BottomBar、GlassBackdrop  │
├──────────────┬──────────────┬────────────────────┤
│ timing/      │ widget/      │ data/json/          │
│ 计时引擎      │ RemoteViews  │ 日程导入导出         │
│ 闹钟/通知     │ 桌面小组件   │ 文本解析            │
│ 前台服务      │              │                     │
├──────────────┴──────────────┴────────────────────┤
│ data/  PastelleRepository（唯一数据出入口）        │
│        Room: TaskEntity / ScheduleEntity / DAO    │
└─────────────────────────────────────────────────┘
```

`PastelleApp`（Application）持有 `repository` 与 `timingEngine` 单例；页面通过 `LocalContext.current.applicationContext as PastelleApp` 取用，未引入 DI 框架。Room 数据库 `pastelle.db` 是唯一持久化存储，无网络层。

## 状态与口径

- 落库状态：`TaskState { WAITING, PREP, FOCUS, BREAK, COMPLETED }`，由计时引擎驱动。
- 展示状态：`core/TaskDisplay.kt` 把落库状态 + 墙钟统一推导为「待开始 / 进行中 / 已完成 / 未完成」，今日页、日程页、统计页共用同一口径；遗留脏数据（标 `COMPLETED` 但缺 `completedAt`）不再误报已完成。
- 阶段倒计时：`core/TaskStatus.kt` 按 `T0 − 5min → T0 → 35min 专注 / 10min 休息循环 → 计划结束` 推导；时长 < 70 分钟的任务不进入番茄循环，显示整段剩余。
- 今日进度：按任务时长加权求和，`已获贡献 = Σ(时长 × 完成度) / Σ(计划时长)`。

## 关键机制

**时间占比布局**：星期轮盘编辑态把一天映射成竖向时间轴，任务块高度与 `durationMin` 成正比；展示态把同一批任务渲染成居中卡片列表，保持同样的占比关系。

**两态切换**：`WeekWheelScreen` 内 `editing` 布尔态；双指向内划进入编辑态、向外划退出，编辑态隐藏底部胶囊避免遮挡「＋」；`BackHandler` 与「完成」按钮走同一退出路径。外部入口（今日页「安排今天」、小组件「去安排」、日程分享 Intent）通过 `MainActivity` 的路由参数 `ROUTE_SCHEDULE_EDIT` 直接落到当天编辑态。

**编辑即时落库**：轮盘对 `days` 的每次改动立即 `saveWheelDay` 写库，没有延迟批量保存或「未保存」提示；退出编辑不需要额外保存动作。

**提醒链路**：`TimingEngine` → `AlarmScheduler`（`AlarmManager` 精确闹钟）→ `AlarmReceiver` → `Notifications`；`BootReceiver` 在开机后重建计划，`TimingService` 以前台服务维持专注计时。精确闹钟与通知权限状态在「我的」页集中可见。

**桌面小组件**：`TodayWidget` 用 `RemoteViews` 渲染，布局 `widget_today_v8.xml` + 一组 `widget_glass_*.xml` drawable 模拟玻璃质感。刷新来源是真实数据写入（`APPWIDGET_UPDATE` 为受保护广播，不能自行发送）、`SCREEN_ON` / `USER_PRESENT`、以及自身的分钟级 `TICK` 闹钟；因此倒计时每分钟变化，而不是靠 30 分钟的 `updatePeriodMillis` 兜底。launcher 对同名资源有粘滞缓存，视觉改版靠**布局文件改名**（v4 → … → v8）强制重绑。

**视频背景**：`VideoBackground` 位于 `AppNav` 的 `NavHost` 之外、整个导航图最底层，保证切页不重建播放器；页面自己的 `PastelBackground` 只画半透明淡紫调和层。Compose 无 backdrop-filter，玻璃感全部用同族雾面 + 径向色晕 + 镜面描边模拟，兼容 minSdk 26。

## 已知取舍

- 应用 id 沿用历史包名 `com.pastelle.todo`，改名会导致已安装用户数据丢失。
- 玻璃质感是视觉模拟而非真高斯模糊（真模糊需 API 31+，与 minSdk 26 冲突）。
- 没有云同步与多端能力，定位就是「只在本机的日程提醒」。
