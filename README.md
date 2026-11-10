# Pre2do

> 把一天的时间摊开来看：按时间占比安排日程，到点提醒，专注计时，桌面常驻。

Pre2do 是一款 Android 原生的日程提醒 App（Kotlin + Jetpack Compose）。它和普通清单的区别在于：任务不是一行文字，而是一块**按真实时长占据高度的时间块**；今天的进度、下一个任务、还剩多久，始终以「时间」而不是「条数」呈现。

所有数据保存在本机 Room 数据库中：**不联网、不需要账号、不上传任何数据**。

---

## 功能

### 今日
- 焦点任务卡：标题、状态、倒计时、番茄钟进度一目了然
- 日历与「接下来」列表；空态直接给出「安排今天」入口，不用先摸索去哪新建任务
- 首次启动即申请通知权限，避免提醒静默失效；「我的」页可随时检查通知 / 精确闹钟 / 电池优化三项状态

### 日程
- **展示态**是居中的任务卡片列表，卡片高度按任务时长占比分配
- **编辑态**是星期轮盘：双指向内划进入、向外划或点「完成」退出；可拖动改时段、改标题、换颜色、删除（删除有二次确认）
- 编辑即时落库，没有「未保存」状态，切走或返回不会丢改动

### 专注
- 计划时长 ≥ 70 分钟的任务才启用番茄钟，按任务独立计数；短任务整段连续专注
- 35 分钟专注 / 10 分钟休息循环，开始时间前 5 分钟进入准备阶段
- 完成窗口按任务计划时长动态开闸与强制结算
- 前台服务保活，精确闹钟 + 通知提醒，开机后自动恢复计划

### 统计 / 我的
- 番茄数、专注时长、按时率与本周趋势、每日明细
- 日程 JSON 导入 / 导出；也可从其他 App 分享文本或 `.json` 直接进入预览导入（格式见 [`schedule-import-example.json`](schedule-import-example.json)）
- 一键把桌面小组件钉到桌面，或给出长按添加的手工路径

### 桌面小组件「今日速览」
- `RemoteViews` 实现的淡紫液态玻璃卡片：镜面描边、径向折射色晕、同心开环进度、超大倒计时
- **每分钟刷新倒计时**，数据写入、解锁、开机、定时兜底都会即时重绘
- 点击卡片打开 App，「去安排」直达当天日程编辑态

---

## 视觉

- **真视频动态背景**：`res/raw/bg_flow.mp4` 是裁去黑边后正放 + 倒放拼接的循环片段，由全局唯一的 `VideoBackground`（`TextureView` + `MediaPlayer`）挂在导航图最底层；各页只叠半透明淡紫调和层
- **iOS 26 风格液态玻璃底栏**：长椭圆胶囊，低透明度同族雾面 + 径向色晕 + 镜面描边 + 色散细线 + 缓慢流动斜光；动画相位只在 draw phase 读取，零重组
- **程序化音效**：七种交互音色由 `AudioTrack` 实时合成，不含任何第三方音频素材
- 全 App 统一淡紫色系

> 技术说明：Compose 没有 backdrop-filter，真背景模糊需要整页 `GraphicsLayer.record` 两遍且要求 API 31+，与 minSdk 26 冲突。因此玻璃质感用同族雾面 + 色晕做视觉模拟，而不是真高斯模糊。

---

## 快速开始

| 环境 | 要求 |
| --- | --- |
| JDK | 17+（推荐 21） |
| Android SDK | Platform 35、Build Tools 35 |
| Gradle | 8.11.1（wrapper 自带，无需预装） |
| 最低系统 | Android 8.0（API 26），目标 API 35 |

```bash
git clone https://github.com/xxxmean0307/Pre2do.git
cd Pre2do

# 指定本机 SDK：设置 ANDROID_HOME，或写 local.properties（该文件不入库）
export ANDROID_HOME=/path/to/Android/Sdk

./gradlew assembleDebug       # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest   # 26 个单元测试
```

Windows 把 `./gradlew` 换成 `gradlew.bat`，也可以直接用 Android Studio 打开工程根目录。首次构建会由 Gradle 从 Google Maven / Maven Central 拉取依赖，需要网络。

---

## 使用逻辑速览

| Tab | 作用 |
| --- | --- |
| **今日** | 看现在该做什么、还剩多久；直接从空态进入安排 |
| **日程** | 展示态看任务卡片；双指内划进轮盘编辑态调整时间块 |
| **统计** | 回顾完成情况与趋势 |
| **我的** | 权限检查、导入导出、桌面小组件、关于 |

任务状态在 UI 层统一为「待开始 / 进行中 / 已完成 / 未完成」四态，由 `TaskState` 与墙钟共同推导，避免同一任务在不同页面显示不同口径。完整分层见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

---

## 数据与权限

数据只写本机 Room 数据库（库名 `pastelle.db`），没有网络权限、没有账号体系、没有统计上报。

| 权限 | 用途 |
| --- | --- |
| `POST_NOTIFICATIONS` | 任务准备 / 开始 / 专注结束提醒（Android 13+ 首次启动主动申请） |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | 精确时间触发提醒 |
| `RECEIVE_BOOT_COMPLETED` | 重启后恢复提醒计划 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 专注计时不被系统回收 |
| `VIBRATE` | 提醒震动反馈 |

> 历史原因：应用 id / 命名空间仍为 `com.pastelle.todo`（早期项目名 Pastelle），为不破坏已安装用户的数据未做更改；Gradle 项目名与 App 显示名已统一为 Pre2do。

---

## 工程结构

```
Pre2do/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/com/pastelle/todo/
│       │   │   ├── core/        # 时间推导、状态口径、常量、程序化音效
│       │   │   ├── data/        # Room 实体 / DAO / Repository / JSON 解析
│       │   │   ├── timing/      # 计时引擎、闹钟、前台服务、通知、开机恢复
│       │   │   ├── ui/          # Compose 页面与组件（home / weekwheel / execution / stats / more / nav / theme）
│       │   │   └── widget/      # TodayWidget（RemoteViews）
│       │   ├── res/             # 布局、drawable、字体、字符串、视频背景
│       │   └── AndroidManifest.xml
│       └── test/                # 4 个测试类 / 26 个单元测试
├── gradle/wrapper/              # Gradle 8.11.1 wrapper
├── docs/                        # 架构说明、第三方资源许可
├── schedule-import-example.json # 日程导入格式示例
├── build.gradle.kts
└── settings.gradle.kts
```

`app/build/`、`.gradle/`、`local.properties`、`.idea/` 等均由 `.gitignore` 排除。

---

## 测试

```bash
./gradlew testDebugUnitTest
```

覆盖时间 / 番茄数学（`TimingMathTest`）、成长数值（`GrowthMathTest`）、日程 JSON 解析（`ScheduleParserTest`）、问候语时段（`GreetingTest`）。

---

## 第三方资源与许可

字体、动态背景素材、音效与依赖库的许可见 [`docs/THIRD_PARTY_NOTICES.md`](docs/THIRD_PARTY_NOTICES.md)。

## 许可证

[MIT](LICENSE) © 2026 xxxmean0307
