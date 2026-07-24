# NoMedia Viewer 📸

一款安卓图片浏览工具，专门扫描和浏览 **.nomedia 文件夹** 中的隐藏图片。采用瀑布流无接缝浏览方式，阅后即焚 + 收藏筛选机制，帮助你快速筛选和整理图片。

## ✨ 特性

- 🔍 **扫描 .nomedia** — 自动发现手机中所有包含 `.nomedia` 文件的目录
- 🌊 **无接缝瀑布流** — 图片连续排列，上下滑动无缝切换，像翻阅 PDF 一样流畅
- ❤️ **双击收藏** — 双击任意图片即加入收藏夹，红心动效反馈
- 👁️ **阅后即焚** — 默认看过不再显示，专注筛选未看图片
- 🔄 **一键重置** — 重置浏览历史，所有图片重新可见
- 🗂️ **收藏管理** — 底部导航切换收藏页面，网格展示/管理

## 📱 截图

_(截图待补充)_

## 🛠 技术栈

| 技术 | 用途 |
|------|------|
| **Kotlin** | 开发语言 |
| **Jetpack Compose** | UI 框架 |
| **Coil** | 图片异步加载 |
| **Material 3** | 设计语言 |
| **SharedPreferences** | 收藏/浏览历史持久化 |
| **Gradle 8.2** | 构建系统 |
| **minSdk 26 / targetSdk 34** | 兼容范围 |

## 🔧 构建

```bash
# 1. 克隆项目
git clone https://github.com/YOUR_USERNAME/NoMediaViewer.git
cd NoMediaViewer

# 2. 设置 Android SDK 路径 (或创建 local.properties)
export ANDROID_HOME=/path/to/android-sdk

# 3. 构建 Debug APK
./gradlew assembleDebug

# APK 输出路径:
# app/build/outputs/apk/debug/app-debug.apk
```

**环境要求：**
- JDK 17+
- Android SDK 34+
- Android Gradle Plugin 8.2+

## 📦 安装

从 [Releases](https://github.com/YOUR_USERNAME/NoMediaViewer/releases) 页面下载最新 APK，或自行构建。

> ⚠️ 首次运行需要授予**存储权限**（Android 11+ 需要"管理所有文件"权限），因为 `.nomedia` 文件中的图片不被系统媒体库索引。

## 📁 项目结构

```
NoMediaViewer/
├── app/
│   ├── build.gradle.kts          # 模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml   # 权限 & Activity 声明
│       ├── res/                  # 资源文件 (主题, 图标)
│       └── java/com/nomedia/viewer/
│           ├── MainActivity.kt          # 主入口 & 权限处理
│           ├── MainViewModel.kt         # 状态管理
│           ├── ImageRepository.kt       # 数据层 (扫描, 收藏, 历史)
│           └── ui/
│               ├── BrowseScreen.kt      # 瀑布流浏览页面
│               ├── FavoritesScreen.kt   # 收藏管理页面
│               └── theme/Theme.kt       # 主题配色
├── build.gradle.kts             # 根项目配置
├── settings.gradle.kts          # 项目设置
├── gradle.properties            # Gradle 属性
└── README.md                    # 本文件
```

## 📄 License

[MIT](LICENSE)

---

**Made with ❤️ by [Your Name]**
