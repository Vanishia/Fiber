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
- 📁 **多库管理**：支持多个笔记库，无缝切换
- 🔍 **全文搜索**：支持在独立搜索页中检索笔记
- 🎨 **Markdown 预览**：实时预览，编辑模式切换
- 💾 **本地优先**：文件存储在本地 SAF 目录，兼容 Obsidian
- 🚀 **原生性能**：Kotlin + Jetpack Compose

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
| **UI** | Material 3 | 设计系统 |
| **UI** | Navigation Compose 2.8+ | 页面导航 |
| **数据** | Room 2.6+ | 本地数据库（笔记库 + 文件元数据）|
| **数据** | Paging 3 | 分页加载（数据库级分页）|
| **数据** | SAF | 文件系统访问 |
| **架构** | Hilt | 依赖注入 |
| **架构** | StateFlow | 响应式状态管理 |
| **工具** | Timber | 日志管理 |
| **工具** | Markwon | Markdown 渲染 |

---

## 📐 结构详解

### 1. UI 层（`ui/screens/`）

每个功能模块遵循 **Screen + ViewModel + UiState** 模式：

```kotlin
// 页面入口（纯展示逻辑）
@Composable
fun FileListScreen(viewModel: FileListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    // UI 渲染
}

// 状态管理（业务逻辑）
class FileListViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(FileListUiState())
    val uiState: StateFlow<FileListUiState> = _uiState.asStateFlow()
}

// 状态定义（不可变数据类）
data class FileListUiState(
    val files: Flow<PagingData<MarkdownFile>> = emptyFlow(),
    val isLoading: Boolean = false,
    val error: FileError? = null
)
```

**关键设计**：
- **单向数据流**：UI → Event → ViewModel → UiState → UI
- **事件封装**：使用密封类 `FileListEvent` 定义用户操作
- **组件化**：复杂 UI 拆分为独立组件（`components/` 包）

### 2. Domain 层（`domain/`）

封装核心业务逻辑，与 UI 和数据层解耦：

| 类型 | 示例 | 职责 |
|------|------|------|
| **UseCase** | `CreateMarkdownFileUseCase` | 单一业务操作（创建文件的完整流程）|
| **UseCase** | `GenerateFileNameUseCase` | 文件名生成策略（时间戳格式）|
| **Manager** | `LibrarySyncManager` | 库生命周期管理（添加/同步/清理）|

**优势**：业务逻辑可独立测试，不受 UI 框架影响。

### 3. Data 层（`data/`）

#### 3.1 Repository 模式

```kotlin
// 接口定义（抽象契约）
interface FileRepository {
    suspend fun readFile(fileUri: String): ContentResult<String>
    suspend fun createFile(folderUri: String, fileName: String): ContentResult<String>
}

// 实现（SAF 文件系统）
class FileRepositoryImpl @Inject constructor(...) : FileRepository
```

**设计原则**：UI 层依赖 `FileRepository` 接口，不感知 SAF 实现细节。

#### 3.2 Room 数据库架构

| 表 | 用途 |
|----|------|
| `LibraryEntity` | 笔记库信息（名称、URI、创建时间）|
| `MarkdownFileEntity` | 文件元数据（文件名、修改时间、预览摘要）|

**关键设计**：
- **元数据与文件分离**：文件内容存 SAF，元数据存 Room
- **索引器（`FileIndexer`）**：定期同步文件系统状态到数据库
- **分页加载**：DAO 返回 `PagingSource`，配合 Paging 3 实现真正的分页

#### 3.3 事件总线（`EventBus`）

使用 `SharedFlow` 实现跨页面通信：

```kotlin
// 全局事件定义
sealed class AppEvent {
    data class LibraryChanged(val libraryId: String) : AppEvent()
    data class FileCreated(val fileUri: String) : AppEvent()
}

// 发送事件
EventBus.emit(AppEvent.LibraryChanged(libraryId))

// 监听事件
EventBus.events.collect { event -> ... }
```

### 4. 关键功能实现

#### 4.1 文件列表分页（Paging 3 + Room）

```kotlin
// DAO 返回 PagingSource
@Query("SELECT * FROM markdown_files ORDER BY modified_time DESC")
fun getPagingSource(): PagingSource<Int, MarkdownFileEntity>

// ViewModel 配置分页
Pager(
    config = PagingConfig(pageSize = 20, prefetchDistance = 10),
    pagingSourceFactory = { dao.getPagingSource() }
).flow.cachedIn(viewModelScope)
```

**优势**：
- 真正的数据库级分页（非内存分页）
- 大库启动不白屏（只加载首屏数据）
- 滚动时自动加载下一页

#### 4.2 预览系统（内存缓存 + 懒加载）

```kotlin
// 三级预览策略：
// 1. 内存缓存（PreviewCache）：已加载的预览直接返回
// 2. 数据库字段：metadata.preview（快速显示）
// 3. 文件系统：实时读取（首次加载）

class PreviewCache {
    private val cache = LruCache<String, String>(maxSize = 50)
    fun get(key: String): String? = cache.get(key)
    fun put(key: String, value: String) = cache.put(key, value)
}
```

#### 4.3 多库管理（SAF + URI 持久化）

- 使用 `DocumentsContract` API 操作 SAF 文件
- URI 权限持久化：`takePersistableUriPermission()`
- 库切换时释放旧权限，避免权限累积

### 5. 模块结构

```
app/src/main/java/com/bird/fiber/
├── MainActivity.kt              # 导航容器 + SAF 权限处理
├── FiberApplication.kt          # 应用入口（HiltAndroidApp）
├── di/
│   └── AppModule.kt             # Hilt 模块（数据库、Repository 绑定）
├── data/
│   ├── local/                   # 本地数据源
│   │   ├── library/             # Room 数据库（Entity + DAO）
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
    │   ├── sidebar/             # 侧边栏（库管理）
    │   ├── search/              # 搜索（文件名搜索）
    │   ├── settings/            # 设置（字体、主题、配色）
    │   └── quicknote/           # 快速笔记（底部输入框）
    └── theme/                   # Material 3 主题
```

---

## 🚀 快速开始

### 环境要求

- Android Studio 最新稳定版
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

---

## 📖 使用指南

### 1. 添加笔记库

首次使用需要添加笔记库（文件夹）：
1. 点击"选择文件夹"
2. 选择一个文件夹（如 `Documents/Notes`）
3. 授权访问权限

### 2. 快速记录

**方式 1：快速记录（推荐）**
- 底部输入框直接输入内容
- 点击纸飞机图标保存
- 文件自动命名：`YY-MM-DD_HH-mm-ss.md`

**方式 2：自定义文件名**
- 点击右上角编辑图标
- 输入文件名
- 点击创建

### 3. 编辑笔记

- 点击任意笔记进入预览模式（默认）
- 点击右上角编辑图标切换编辑模式
- 点击保存图标保存修改
- 点击眼睛图标切换预览模式

### 4. 搜索笔记

- 点击主页面右上角搜索按钮进入搜索页
- 在搜索页输入关键词
- 当前支持搜索文件名和正文内容

---

## 📄 License

本项目基于 MIT License 开源，详见 [LICENSE](./LICENSE)。

## 🙏 致谢

- 应用图标由项目作者绘制。
- [Obsidian](https://obsidian.md/)
- [Flomo](https://flomoapp.com/)
- [Markor](https://github.com/gsantner/markor)
- 上面三款笔记软件对本项目的产品方向和交互思路影响较大。

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI 框架
