# 可拖动滚动条方案：主界面 / 笔记浏览页 / 编辑器

> **本次实施范围：先做笔记浏览页 + 主界面（§4.1、§4.2）；编辑器（§4.3）暂缓**，编辑器部分保留在方案中作为后续阶段的设计。

## 1. 目标与交互设计

为三个页面添加可上下拖动的滚动条（fast-scroll thumb），核心设计：

- 包装式：不动列表内容，在滚动容器外套一层 `Box`，把滚动条轨道叠在右边缘上层。
- thumb **固定高度 36dp**（不随内容长度变化），宽度闲时 4dp / 拖动时 12dp，透明度三档：Idle 0.12 / 滚动中 0.25 / 拖动 0.55，停止滚动 1200ms 后回落。
- 只有按在 thumb 上才开始拖动（按轨道空白处无反应）；位置映射 `thumbOffset = (trackHeight − thumbHeight) × scrollFraction`，fraction 恒 clamp 到 [0,1]。
- 边界钉死：`!canScrollForward → fraction = 1f`，`!canScrollBackward → 0f`，保证"拖到底就是真底"。

## 2. 现状调查结论（已核实代码）

| | 主界面 | 笔记浏览页 | 编辑器-编辑 | 编辑器-预览 |
|---|---|---|---|---|
| 滚动容器 | `LazyColumn`（Paging 3） | `LazyColumn`（Paging 3） | `BasicTextField` + 外层 `verticalScroll(ScrollState)` | View 体系 `ScrollView` + 单 `TextView`（Markwon） |
| 滚动状态 | `FileListContent.kt:285` `rememberLazyListState()`（私有于 `FileList`） | `NoteListScreen.kt:209` 未显式持有 state | `EditorEditPane.kt:59` `rememberScrollState()`，精确暴露 `value/maxValue/viewportSize` | `scrollView.scrollY`，已禁用系统滚动条（`EditorPreviewPane.kt:110`） |
| 右缘空隙 | 11dp（卡片 contentPadding，`FileListContent.kt:308`） | 11dp（同上，`NoteListScreen.kt:211`） | 16dp（`EditorContentHost.kt:44`） | 16dp（同一 contentModifier） |
| 现成可叠层 Box | `FileList` 内需新包一层 Box | `NoteList` 内需新包一层 Box | `EditorEditPane.kt:210` 根 Box 现成 | `EditorPreviewPane.kt:40` 根 Box 现成 |
| 特殊风险 | 卡片水平滑动手势（`SwipeableFileItem`，**已弃用**，代码保留备用）；后台刷新小菊花在 TopEnd | 无滑动手势、无遮挡，最干净 | 聚焦校正循环（`EditorEditPane.kt:156-180`）；键盘 imePadding | 滚动数据在 View 侧，需桥接到 Compose；渲染 400ms 防抖、图片异步加载导致高度后变 |

其他事实：
- 两个列表页均无下拉刷新、无 sticky header；Paging 均 `enablePlaceholders = false`，`itemCount` 随加载增长（thumb 位置按"可见项/已加载项"估算，追加时的位置回跳通过预留虚拟余量缓解，见 §5）。
- 项目已有 JUnit4 + MockK + coroutines-test + Turbine 纯 JVM 单测惯例（如 `AssociationMenuTest` 测 `ui/components` 下的纯函数）；无 Robolectric、无 Compose UI 测试。
- Compose BOM 2025.09.01，`awaitEachGesture`、`Modifier.offset {}`、`systemGestureExclusion` 均可用，无需新依赖。

## 3. 总体设计

### 新增文件（全部自写）

1. **`app/src/main/java/com/bird/fiber/ui/components/DraggableScrollbarMath.kt`** — 纯函数，不含任何 Compose import（保证纯 JVM 可测）：
   - `scrollFraction(value: Int, max: Int): Float?` — ScrollState 系精确 fraction；`max <= 0` 返回 `null`（表示隐藏）。
   - `scrollTargetForFraction(fraction: Float, max: Int): Int` — 反向映射，round + coerce。
   - `lazyListScrollFraction(firstVisibleIndex, firstVisibleItemScrollOffset..., totalItemsCount, canScrollBackward, canScrollForward, ...): Float?` — LazyList 像素级估算：用首项已滚过比例 + 末项可见比例算连续进度，再归一化；边界钉死；`totalItemsCount` 直接取 `listState.layoutInfo.totalItemsCount`（已含 append loading 占位项）。
   - `lazyListScrollTarget(fraction, totalItemsCount, effectiveSpan, firstItemSize...) : Pair<Int, Int>` — 反向映射 (index, scrollOffset)；**index 必须 clamp 到 `[0, totalItemsCount − 1]`**（即当前 snapshot 已加载范围，Paging 场景 itemCount 动态变化，防越界崩溃）。
   - `fractionToThumbOffset` / `thumbOffsetToFraction`（轨道高、thumb 高 → px 偏移，clamp）。
   - `thumbAlpha(state: Idle/Active/Drag): Float` — 透明度策略。
2. **`app/src/main/java/com/bird/fiber/ui/components/DraggableScrollbar.kt`** — Composable：
   - `DraggableScrollbar(state: LazyListState, modifier)` — 列表页用。`snapshotFlow { state.layoutInfo }` 驱动估算。
   - `DraggableScrollbar(state: ScrollState, modifier)` — 编辑面板用（后续阶段），精确映射。
   - `DraggableScrollbar(metrics: ScrollbarMetrics, onDragFraction: (Float) -> Unit, modifier)` — 通用分数版（后续阶段预览面板用）；`ScrollbarMetrics(value, max, canScroll)` 为普通数据类。
   - 内部共用：Canvas 画圆角 thumb（触摸轨道宽 24dp、thumb 贴最右）、`awaitEachGesture` 手写拖动（仅按在 thumb 热区内才启动，拖时 `offset {}` lambda 直接驱动避免重组，松手 spring 回落）、1200ms 无滚动后 alpha 回落。
   - **手势排除只挂在 thumb 热区上**（随 thumb 位置移动的动态小矩形，约 24dp×36dp），**不对整根轨道加 `systemGestureExclusion()`**：Android 对每侧边缘的系统手势排除区域有总高度上限（约 200dp），全轨道排除会超标失效；只排除 thumb 所在矩形既不超标，轨道空白处的系统侧滑返回也完全不受影响。
   - 拖动性能：拖动中每个事件只做轻量 offset 连续计算；列表滚动请求每次先 cancel 上一个未完成的 `scrollToItem` Job 再发新的，防止快速拖动时协程堆积；ScrollState 用 `scrollTo()`（`MutatorMutex` 天然取消上一次）。
   - **拖拽状态标识**：可选参数 `onDragStateChanged: (Boolean) -> Unit = {}` 暴露"正在拖 thumb"。拖 thumb 引发的 `scrollToItem`/`scrollTo` 与手指滑动一样会置位 `isScrollInProgress`，单靠它无法区分来源；页面若有监听者（如编辑器聚焦校正循环），用 `isScrollInProgress && !isDragging` 即可区分"手指滑动列表"与"按住滑块拖拽"。组件内部 Drag 视觉状态也以此覆盖 Active。
   - 颜色：`MaterialTheme.colorScheme.onSurface` + alpha，亮暗主题通用；`canScroll == false` 时整体不绘制。

### 视觉规格（token）

```
ThumbHeight = 36dp   IdleWidth = 4dp   DragWidth = 12dp   TouchWidth = 24dp
TrackPaddingVertical = 2dp   TrackEndPadding = 2dp
Alpha: Idle 0.12 / Active 0.25 / Drag 0.55   FadeDelay = 1200ms
```

触摸热区取 24dp：主界面卡片右缘只有 11dp 空隙，24dp 热区侵入卡片约 13dp，把对（已弃用的）滑动手势的抢占限制在 36dp 高的 thumb 小矩形内。

## 4. 分页接入方式（逐页面）

### 4.1 主界面（FileListContent）— 难度：低

- 改动只在 `FileListContent.kt` 的私有 `FileList()` 内：`LazyColumn` 外包一层 `Box`，尾部加
  `DraggableScrollbar(state = listState, modifier = Modifier.align(Alignment.CenterEnd).padding(top = topPadding + 2.dp, bottom = 10.dp))`
  （轨道起始于悬浮顶栏之下，避开 TopEnd 的刷新小菊花——菊花在 `end = 16.dp` 处，thumb 贴右缘 2dp，不重叠）。
- `listState` 不用提升，原逻辑（键盘收起上报、scrollToTopSignal）原样保留。
- **布局零改动**：卡片左右仍各 11dp；idle 4dp thumb 落在空隙内，拖动时 12dp 宽临时压卡片右缘约 1–3dp，可接受。
- 手势冲突：用户已确认卡片的水平滑动删除/编辑手势**基本弃用**（代码保留备用）。事实上主页面 `FileListItem` 未传 `swipeEnabled`（默认 true），手势层仍在接线中（`FileListContent.kt:317`、`SwipeableFileItem.kt:143`）；即便如此，thumb 热区（24dp 宽 × 36dp 高）与滑动手势的抢占也仅限"恰好按在 thumb 小矩形上"的起手，影响面极小。若日后彻底移除滑动手势，则冲突完全消失。

### 4.2 笔记浏览页（NoteListScreen）— 难度：低

- `NoteList()` 内补 `val listState = rememberLazyListState()` 传给 LazyColumn，同样外包 Box + overlay，padding 规格与主界面一致。
- 无滑动手势（`swipeEnabled = false`）、无刷新菊花，是三页中最干净的接入。

### 4.3 编辑器（本次暂缓，后续阶段）— 难度：高（两个子系统难度差异大）

编辑与预览互斥切换（`EditorContentHost` when 分支），两侧各自接滚动条：

**(a) 编辑面板（EditorEditPane）— 难度：低**
- 根 `Box`（`EditorEditPane.kt:210`，带 `imePadding()`）内直接加：
  `DraggableScrollbar(state = scrollState, modifier = Modifier.align(Alignment.CenterEnd).padding(top = topContentInset, bottom = bottomContentInset + 2.dp))`
- `ScrollState` 的 `value/maxValue` 精确，无需估算；键盘弹起时 Box 被 imePadding 收缩，轨道随之上移，行为自然正确。
- 与聚焦校正循环的交互：拖 thumb 触发 `scrollTo` → `isScrollInProgress = true` 且非 programmatic → 校正循环按既有语义退出（等同用户手动滚动），**无需改校正逻辑**；`revealCursorIfNeeded` 只监听 ime/viewport，不会被拖 thumb 误触发。
- 拖 thumb 同时会经既有 `onScrollFractionChanged` 上报 fraction，切到预览位置自然衔接。

**(b) 预览面板（EditorPreviewPane）— 难度：中高**
- 滚动数据在 View 侧，新增桥接：在 `MarkdownPreview` 内 `remember { PreviewScrollBridge() }`（普通类，含 `metrics: ScrollbarMetrics` 的 Compose State + `scrollToFraction: (Float) -> Unit` 持有 ScrollView 引用）。
- `factory` 里：`setOnScrollChangeListener` 除既有 fraction 上报外，同步更新 `bridge.metrics`（scrollY / maxScroll）；另加 `ViewTreeObserver.OnGlobalLayoutListener`（或复用 `doOnPreDraw` 时机）在内容重排（渲染防抖完成、图片加载后高度变化）后刷新 `maxScroll/viewport`。`bridge.scrollToFraction = { f -> scrollView.scrollTo(0, (f * maxScroll).roundToInt()) }`。
- `EditorPreviewPane` 外层 Box 内加通用分数版 `DraggableScrollbar(metrics, onDragFraction = bridge.scrollToFraction, ...)`，叠在 `AndroidView` 之上，padding 同编辑面板。
- `maxScroll <= 0`（内容不足一屏，`isFillViewport`）时 metrics.canScroll = false → thumb 隐藏。
- 预览 TextView 文字从 16dp padding 后才排版，thumb 画在 16dp 边距内，不压文字，不影响文本选择/链接点击（热区只有按下判定在 thumb 上才消费）。

## 5. 边界情况

- **内容不可滚**（空列表、短笔记、maxValue = 0）→ 不绘制 thumb（fraction 返回 null）。
- **Paging 追加加载**：`totalItemsCount` 增长使同位置 fraction 变小，thumb 位置会跳动——分页未加载完时（`loadState.append.endOfPaginationReached == false`）调用方传入一页大小的 `extraUnloadedItems` 虚拟余量：估算分母计入余量，thumb 行程压缩在轨道上部，且已加载末尾不再钉死 1f，追加加载引发的回跳幅度同比缩小；分页加载完后传 0，恢复全程映射与精确钉底。追加 loading 占位项计入 totalItemsCount，不特殊处理。
- **拖到底/顶**：`canScrollForward=false → 1f`、`canScrollBackward=false → 0f` 钉死，保证拖动往返能到达真实两端。
- **系统右缘返回手势**：`systemGestureExclusion()` 只挂在 thumb 热区（随 thumb 移动的动态矩形，约 24dp×36dp），而非整根轨道——Android 每侧边缘排除区域总高上限约 200dp，全轨道排除会超标失效。效果：轨道空白处系统侧滑返回完全正常，只有 thumb 当前所在的 36dp 高小条内右滑优先给滚动条。
- **快速连续拖动**：拖动中只做轻量 offset 连续计算；每次滚动请求先 cancel 上一个未完成的 Job 再发新的，防止协程堆积卡顿；`lazyListScrollTarget` 的 index clamp 到 snapshot 的 `itemCount − 1`，快速拖到底部、Paging 并发加载导致 itemCount 变化时不越界、不崩溃。
- **拖拽中的滚动状态标识**：拖 thumb 触发的代码滚动与手指滑动一样置位 `isScrollInProgress`，单靠它无法区分来源；组件通过 `onDragStateChanged` 暴露拖拽态，需要区分的页面组合 `isScrollInProgress && !isDragging` 判断。本次两列表页没有依赖该区分的逻辑（主界面收起键盘按 offset 变化触发，拖 thumb 时收起键盘属预期行为）。
- **键盘弹起**（编辑面板，后续阶段）：viewport 变小、maxValue 不变，fraction 重算，轨道随 imePadding 收缩。
- **编辑/预览切换**（后续阶段）：既有 ScrollFractionHolder 机制不受影响，thumb 拖动上报的 fraction 直接复用该链路。
- **暗色/亮色主题对比度**：`onSurface` + 三档 alpha 在常规背景下对比度足够；但 Idle 档 0.12 在浅色卡片背景上可能偏弱，需在亮、暗两种主题下实机肉眼确认其清晰可见且不突兀，必要时上调 Idle alpha（如 0.2）。

## 6. 风险评估

| 风险 | 影响面 | 等级 | 缓解 |
|---|---|---|---|
| 主界面滑动手势被 thumb 抢占 | 主界面 | 低 | 手势已弃用（代码保留备用）；热区仍限 24dp×36dp、按下即判定 |
| 右缘返回手势被 thumb 抢占 | 两列表页 | 低 | 手势排除只挂 thumb 动态小矩形（≤36dp 高，远低于 200dp 上限）；轨道空白处返回手势不受影响 |
| 快速拖动触发 Paging 并发加载 / 目标 index 越界 | 两列表页 | 低 | 拖动中只做轻量计算；cancel 上一 Job 再发新请求；index clamp 到 snapshot `itemCount − 1` |
| Paging 下 thumb 位置跳动 | 两列表页 | 低 | 固定高度 thumb + 像素级估算 + 边界钉死 |
| Idle 档 0.12 在浅色卡片背景上不清晰 | 两列表页 | 低 | 亮暗主题实机确认，必要时上调 alpha |
| 预览面板高度异步变化导致 fraction 失准 | 编辑器预览（暂缓） | 中 | OnGlobalLayoutListener 刷新 metrics；滚动时实时上报 |
| 编辑器聚焦校正循环被拖 thumb 打断 | 编辑器编辑（暂缓） | 低（符合预期） | 语义等同用户手动滚动，既有逻辑自然覆盖 |
| `animateItem()` 动画中 layoutInfo 抖动 | 两列表页 | 低 | 估算每帧重算，天然跟随 |

## 7. 难度与影响总评

- **笔记浏览页：低**。加一个 state + 包一层 Box，无手势冲突，约 20 行改动。
- **主界面：低**。同样包 Box；滑动手势已弃用，刷新菊花与轨道区域不重叠；布局不变。
- **编辑器-编辑面板（暂缓）：低**。ScrollState 精确映射，根 Box 现成，一行 overlay。
- **编辑器-预览面板（暂缓）：中高**。唯一需要新增"View→Compose 滚动桥"的地方，要处理渲染防抖与图片异步加载后的高度刷新；不改动 Markwon 渲染与既有滚动恢复逻辑。
- 对现有布局：**零 padding 改动**（11dp/16dp 空隙均够放 4dp thumb，不需要压缩卡片边距）。

## 8. 测试方案

### 8.1 纯 JVM 单测（新增 `app/src/test/java/com/bird/fiber/ui/components/DraggableScrollbarMathTest.kt`，沿用 AssociationMenuTest 风格）

`DraggableScrollbarMath` 全部设计为纯函数，覆盖：

1. `scrollFraction`：max=0 → null；value=0 → 0f；value=max → 1f；越界 clamp。（本次编辑器暂缓，该函数先行实现但不接入页面，测试照写）
2. `scrollTargetForFraction` 往返：`fraction=1f` 映射到 `maxValue`（拖到底到真底）；0→0；与 `scrollFraction` 互为逆运算（误差 ≤1px）。
3. `lazyListScrollFraction`：
   - 首项完全可见且 `canScrollBackward=false` → 精确 0f；
   - `canScrollForward=false` → 精确 1f（钉死优先于估算误差）；
   - 等高 item 的中点位置 → fraction 符合线性预期；
   - **不等高 item**（卡片预览长度不同）→ 用首项像素偏移修正，结果单调递增；
   - totalItemsCount ≤ 可见项数（内容不足一屏）→ null。
4. `lazyListScrollTarget`：fraction→(index, offset) 与正向估算 round-trip 一致；**fraction=1f 时 index clamp 到 `itemCount − 1`**（Paging itemCount 动态变化防越界）；offset 不越界。
5. thumb 偏移映射：fraction 0/0.5/1 → 偏移 0/中点/trackHeight−thumbHeight；反向还原；轨道高 < thumb 高时不除零。
6. `thumbAlpha`：Idle/Active/Drag → 0.12/0.25/0.55。

### 8.2 回归验证

- `./gradlew :app:testDebugUnitTest` — 新增测试 + 全部既有单测通过。
- `./gradlew :app:assembleDebug` — 编译通过。

### 8.3 人工验收清单（真机/模拟器）

本次范围（两列表页）：
- 笔记浏览页：拖 thumb 平滑滚动到底/顶；内容不足一屏时 thumb 不显示；滚动停止 1.2s 后 thumb 变淡。
- 主界面：同上；另外验证快速拖动 thumb 到底部触发 Paging 追加加载时不卡死、不崩溃；新建笔记后 scrollToTop 动画时 thumb 跟随。
- 系统手势：右缘侧滑返回在 thumb 所在小矩形以外（含轨道空白处）正常触发。
- 对比度：亮、暗两种主题下实机确认 Idle 档 0.12 透明度在卡片背景上清晰可见且不突兀，必要时上调至 ~0.2。

后续阶段（编辑器，本次不做）：
- 编辑：长文档拖 thumb 到两端能到真顶/真底；键盘弹起时轨道收缩且不消失；拖 thumb 后点按文本，聚焦校正不把视图拽走。
- 预览：渲染完成/图片加载后 thumb 比例正确；拖 thumb 滚动平滑；切编辑再切回位置衔接。

## 9. 实施顺序

本次（浏览页 + 主界面）：
1. `DraggableScrollbarMath.kt` + 单测（先让映射算法绿）。
2. `DraggableScrollbar.kt`（LazyListState 重载必做；ScrollState / 通用分数重载一并写好，编辑器阶段直接可用）。
3. 笔记浏览页接入（最简单，先验证组件）。
4. 主界面接入。
5. 全量单测 + 编译 + 人工验收。

后续阶段（编辑器，本次不动）：编辑面板 overlay 一行接入 → 预览面板 View 滚动桥接入。

本次改动文件清单：新增 2 个组件文件 + 1 个测试文件；修改 `NoteListScreen.kt`、`FileListContent.kt` 共 2 个文件（局部小包改动，不动既有滚动/刷新/置顶逻辑）。编辑器相关文件本次不碰。
