# 第三方资源与许可说明

本仓库只包含可再分发的资源。若你把本项目用于二次分发，请一并保留本文件。

## 字体（`app/src/main/res/font/`）

| 字体 | 字重文件 | 许可 |
| --- | --- | --- |
| Bungee | `bungee.ttf` | SIL Open Font License 1.1 |
| Baloo 2 | `baloo2_bold.ttf`、`baloo2_extrabold.ttf` | SIL Open Font License 1.1 |
| Righteous | `righteous.ttf` | SIL Open Font License 1.1 |
| Nunito | `nunito_bold.ttf`、`nunito_semibold.ttf` | SIL Open Font License 1.1 |

字体来自 [Google Fonts](https://fonts.google.com/)，依据 SIL OFL 1.1 随应用打包分发。OFL 全文见 <https://openfontlicense.org/>。字体文件本身保持原样、未做修改。

## 动态背景视频（`app/src/main/res/raw/bg_flow.mp4`）

该片段由本项目的素材裁去黑边、正放与倒放拼接后转码为 H.264 得到，用于 `VideoBackground` 的循环播放。**素材由项目作者提供**；若你把它替换为来自第三方的视频，请自行确认该素材的再分发授权。

## 交互音效

`core/audio/IosSounds.kt` 用 `AudioTrack` 程序化合成七种交互音色（Tick / Tock / Tink / Pop / Ack / Thunk / Swoosh），**不包含任何来自 Apple 或第三方的音频素材**。

## 应用图标与插画

`res/drawable/`、`res/mipmap-anydpi-v26/` 下的图标与装饰均为本项目的 vector drawable，无外部素材。

## 依赖库

| 依赖 | 许可 |
| --- | --- |
| AndroidX（Core / Activity / Lifecycle / Room / Navigation） | Apache License 2.0 |
| Jetpack Compose（BOM 2024.12.01） | Apache License 2.0 |
| kotlinx-serialization-json | Apache License 2.0 |
| Kotlin 标准库 | Apache License 2.0 |
| Android Gradle Plugin、Kotlin Gradle Plugin、KSP（构建期） | Apache License 2.0 / 各自发行许可 |
| Gradle 8.11.1（构建期，通过 wrapper 下载） | Apache License 2.0 |

依赖版本以 `build.gradle.kts` 为准。依赖本身不随本仓库分发，clone 后由 Gradle 从 Google Maven / Maven Central 拉取。
