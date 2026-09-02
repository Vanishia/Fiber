# Fiber

> 轻量级 Markdown 笔记应用，强调快速记录与本地优先

[![Android](https://img.shields.io/badge/Android-8.1%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-1.7+-purple.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

---

## 📱 简介

Fiber 是一款轻量级的 Markdown 笔记应用，专注于**快速记录**体验。灵感来自 Flomo 和 Obsidian，旨在提供原生 Android 应用的轻快体验，同时保持与 Obsidian 的文件兼容性。

### 核心特性

- ✍️ **快速记录**：底部输入框，打开即写，一键保存
- 📁 **多库管理**：支持多个文件夹笔记库
- 🔍 **全文搜索**：匹配标题、正文和路径，可跨库，结果展示命中片段并高亮关键词
- 🔥 **记录热力图**：侧边栏热力图 + 全年热力图页，支持按月/年筛选，点击色块、按日期查看笔记
- 🎲 **随机漫步**：随机回顾全库或当前库笔记，弹窗全文阅读
- 📥 **外部分享与导入**：接收其他应用分享的 Markdown 文件和图片，可选库保存入库
- 🖼️ **图片与附件**：快速记录和编辑器均可插入图片，支持 Markdown 图片预览
  - 🧹 **附件管理**：查看每个库的图片，大图预览、关联笔记跳转，筛选已关联/未关联附件并批量清理

- 🎨 **个性化主题**：Material 3 Expressive、动态颜色、预设/自定义主题色和深浅色模式
- 👁️ **Markdown 显示**：支持表格、任务列表、删除线和图片，编辑/预览模式切换
- 💾 **本地优先**：文件存储在本地 SAF 目录，兼容 Obsidian等

---

## 🏗️ 技术结构

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                             │
│   (Screens, ViewModels, UiState, Components)                │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                           │
│          (UseCases, SyncManager)                            │
├─────────────────────────────────────────────────────────────┤
│                       Data Layer                            │
│   (Repository, Database/DAO, FileIndexer, EventBus)         │
├─────────────────────────────────────────────────────────────┤
│                    Framework Layer                          │
│       (SAF, Room, Hilt, Compose, Navigation)                │
└─────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 | 用途 |
|------|------|------|
| **UI** | Jetpack Compose 1.7+ | 声明式 UI |
| **UI** | Material 3 Expressive | 设计系统与动态主题 |
| **UI** | Navigation Compose 2.8+ | 页面导航 |
| **数据** | Room 2.6+ | 本地数据库（笔记库 + 文件元数据）|
| **数据** | Paging 3 | 分页加载（数据库级分页）|
| **数据** | SAF | 文件系统访问 |
| **架构** | Hilt | 依赖注入 |
| **架构** | StateFlow | 响应式状态管理 |
| **工具** | Timber | 日志管理 |
| **工具** | Markwon | Markdown 渲染 |
| **工具** | Coil | 图片加载 |

### 模块结构

```
app/src/main/java/com/bird/fiber/
├── MainActivity.kt              # 导航容器 + SAF 权限处理
├── FiberApplication.kt          # 应用入口（HiltAndroidApp）
├── di/
│   └── AppModule.kt             # Hilt 模块（数据库、Repository 绑定）
├── data/
│   ├── local/                   # 本地数据源
│   │   ├── library/             # Room 数据库（Entity + DAO）
│   │   ├── AttachmentRepositoryImpl.kt
│   │   ├── FileIndexer.kt       # 文件索引器
│   │   ├── FileRepositoryImpl.kt
│   │   └── PreviewCache.kt
│   ├── model/                   # 数据模型（密封类）
│   ├── event/                   # 事件总线
│   └── repository/              # Repository 接口
├── domain/
│   ├── usecase/                 # 业务用例
│   └── sync/
│       └── LibrarySyncManager.kt
└── ui/
    ├── navigation/              # 导航图
    ├── screens/                 # 功能页面
    │   ├── main/                # 主屏幕（侧边栏 + 文件列表 + 快速笔记）
    │   ├── filelist/            # 文件列表（Paging 3）
    │   ├── editor/              # 编辑器（Markwon 渲染）
    │   ├── attachments/         # 附件浏览、关联状态与清理
    │   ├── heatmap/             # 记录热力图页（月/年筛选）
    │   ├── importing/           # 外部分享与文件导入
    │   ├── notelist/            # 全部笔记 / 当日笔记浏览页
    │   ├── sidebar/             # 侧边栏（库管理、热力图）
    │   ├── search/              # 全文搜索、范围与排序
    │   ├── settings/            # 设置（字体、主题、配色）
    │   └── quicknote/           # 快速笔记（底部输入框）
    └── theme/                   # Material 3 主题
```

---

## 🚀 快速开始

### 环境要求

- Android Studio 稳定版
- JDK 17+
- Android SDK 27+（Android 8.1+）

### 构建项目

```bash
# 克隆项目
git clone https://github.com/Vanishia/Fiber.git
cd Fiber

# 构建 Debug APK
./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 构建签名 Release APK

发布签名只保存在本机。将签名文件放到 `release/`，并创建不会提交到 Git 的
`release/keystore.properties`：

```properties
storeFile=fiber-release.jks
storePassword=你的密钥库密码
keyAlias=你的密钥别名
keyPassword=你的密钥密码
```

然后执行：

```bash
./gradlew assembleRelease
```

存在上述配置时，Gradle 会自动完成混淆、资源压缩和签名，产物位于
`app/build/outputs/apk/release/app-release.apk`。没有本地签名配置时仍可编译，
但只会得到不能直接发布的未签名 APK。

---

## 📖 使用说明

### 1. 添加笔记库

首次使用需要添加笔记库（文件夹）：
1. 点击"选择文件夹"
2. 选择一个文件夹（如 `Documents/Notes`）
3. 授权访问权限

### 2. 快速记录

**方式 1：快速记录**

- 底部输入框直接输入内容，点击右下角箭头图标保存
- 文件自动命名：`YY-MM-DD_HH-mm-ss.md`

**方式 2：自定义文件名**

- 点击右上角编辑图标输入文件名，创建笔记，进入全屏编辑器页面

### 3. 查看/编辑笔记（编辑器视图）

- 点击任意笔记进入浏览模式
- 点击右上角编辑图标🖊切换编辑模式，点击眼睛图标👁切换预览模式
- 点击顶部标题可重命名文件

### 4. 搜索笔记

可选择搜索当前库或全部笔记库，搜索结果可按相关性或最近修改排序

### 5. 记录热力图

- 点击热力图色块查看对应日期的笔记
- 点击热力图空白处可查看最近一年的热力图、单月热力图

### 6. 随机漫步

在搜索页点击"随机漫步"，随机抽取一篇笔记回顾

- 可按搜索范围切换从全库或当前库抽取

### 7. 插入图片

- 换行后输入 `@`，从关联菜单选择“图片”
- 图片会复制到当前笔记库的附件目录，并以相对路径写入 Markdown

### 8. 从外部导入

- 在其他应用中将 Markdown 文件"用 Fiber 打开"或分享到 Fiber
- 也可直接分享图片到 Fiber
- 保存前可选择目标笔记库，保存成功后直接进入编辑器

### 9. 调整外观

- 在设置页选择跟随系统、浅色或深色模式
- Android 12 及以上可启用系统动态颜色，也可使用色轮自定义主题色
- 字体大小设置会同步应用到整个界面

---

## 📄 License

本项目基于 MIT License 开源，详见 [LICENSE](./LICENSE)。

应用图标由项目作者绘制

## 🙏 致谢

- [Obsidian](https://obsidian.md/)

- [Flomo](https://flomoapp.com/)

- [Markor](https://github.com/gsantner/markor)

  上面三款笔记软件对本项目的产品方向和交互思路影响较大。

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI 框架
