package com.bird.fiber.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 视觉规格
private val ThumbHeight = 36.dp
private val ThumbIdleWidth = 4.dp
private val ThumbDragWidth = 12.dp
private val TouchWidth = 24.dp
private const val FADE_OUT_DELAY_MS = 1200L

/**
 * 可拖动滚动条（LazyColumn / Paging 列表用）。
 *
 * 叠在滚动内容右缘（调用方用 `Modifier.align(Alignment.CenterEnd)` 定位），
 * 不改变列表自身布局。thumb 固定高 36dp，位置 = 滚动比例 × 可拖行程；
 * 内容不足一屏时整条不绘制。
 *
 * @param extraUnloadedItems 已加载末尾之后仍有未加载内容（Paging 分页未完）时
 * 预留的虚拟项数，调用方按页大小传入；分页加载完后传 0 恢复全程映射与钉底
 * @param onDragStateChanged 拖 thumb 开始/结束回调。拖 thumb 引发的代码滚动与手指
 * 滑动一样会置位 `isScrollInProgress`，需要区分来源的页面用该回调组合判断
 */
@Composable
fun DraggableScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    extraUnloadedItems: Int = 0,
    onDragStateChanged: (Boolean) -> Unit = {}
) {
    // rememberUpdatedState：分页加载完后 extraUnloadedItems 变化要让 derivedStateOf 感知
    val extraUnloaded = rememberUpdatedState(extraUnloadedItems)
    val fractionState = remember { derivedStateOf { estimateLazyListFraction(state, extraUnloaded.value) } }
    val scope = rememberCoroutineScope()
    // 快速拖动时每个事件都会发起滚动：先取消上一个未完成的请求，避免协程堆积
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) {
        onDispose { scrollJob?.cancel() }
    }
    ScrollbarTrack(
        fractionState = fractionState,
        isContentScrolling = state.isScrollInProgress,
        onDragFraction = { fraction ->
            val layoutInfo = state.layoutInfo
            val total = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (total > 0 && visibleItems.isNotEmpty()) {
                val (index, offset) = lazyListScrollTarget(
                    fraction = fraction,
                    totalItemsCount = total,
                    visibleSpan = estimateVisibleSpan(layoutInfo, state.firstVisibleItemScrollOffset),
                    referenceItemSizePx = visibleItems.first().size,
                    extraUnloadedItems = extraUnloadedItems
                )
                // Paging 的 totalItemsCount 可能在估算后变化，发起前再钳一次防越界
                val safeIndex = index.coerceIn(0, total - 1)
                scrollJob?.cancel()
                scrollJob = scope.launch { state.scrollToItem(safeIndex, offset) }
            }
        },
        onDragStateChanged = onDragStateChanged,
        modifier = modifier
    )
}

/**
 * 可拖动滚动条（ScrollState 容器用，value/maxValue 精确，无需估算）。
 * 编辑器暂缓，暂未接入页面。
 */
@Composable
fun DraggableScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    onDragStateChanged: (Boolean) -> Unit = {}
) {
    val fractionState = remember { derivedStateOf { scrollFraction(state.value, state.maxValue) } }
    val scope = rememberCoroutineScope()
    ScrollbarTrack(
        fractionState = fractionState,
        isContentScrolling = state.isScrollInProgress,
        // scrollTo 的 MutatorMutex 会取消上一次未完成的滚动，天然防协程堆积
        onDragFraction = { fraction ->
            scope.launch { state.scrollTo(scrollTargetForFraction(fraction, state.maxValue)) }
        },
        onDragStateChanged = onDragStateChanged,
        modifier = modifier
    )
}

/** View 体系滚动源（如预览面板 ScrollView，后续阶段）上报的滚动数据 */
class ScrollbarMetrics(
    val value: Int,
    val max: Int,
    val isScrolling: Boolean = false
)

/**
 * 通用分数版 [DraggableScrollbar]：滚动数据来自 Compose 之外时使用。
 * 拖 thumb 时通过 [onDragFraction] 回调目标比例，由调用方驱动真实滚动源。
 * 编辑器预览面板（后续阶段）使用。
 */
@Composable
fun DraggableScrollbar(
    metrics: ScrollbarMetrics,
    onDragFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragStateChanged: (Boolean) -> Unit = {}
) {
    val fractionState = rememberUpdatedState(scrollFraction(metrics.value, metrics.max))
    ScrollbarTrack(
        fractionState = fractionState,
        isContentScrolling = metrics.isScrolling,
        onDragFraction = onDragFraction,
        onDragStateChanged = onDragStateChanged,
        modifier = modifier
    )
}

/**
 * 轨道 + thumb 的共用实现。
 *
 * 只有按在 thumb 热区（宽 [TouchWidth] × 高 [ThumbHeight]，随 thumb 移动）内才启动拖动，
 * 轨道其余区域不消费触摸。系统手势排除只挂在 thumb 热区上（远低于系统每侧约 200dp
 * 的排除上限），轨道空白处的侧滑返回不受影响。
 *
 * fraction 经 State 传入并只在 draw/layout 阶段读取，滚动每帧变化不触发重组。
 */
@Composable
private fun ScrollbarTrack(
    fractionState: State<Float?>,
    isContentScrolling: Boolean,
    onDragFraction: (Float) -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // 只在"隐藏↔显示"切换时重组；每帧的 fraction 变化不进入组合阶段
    val visible = remember { derivedStateOf { fractionState.value != null } }
    if (!visible.value) return

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val thumbColor = MaterialTheme.colorScheme.onSurface
    val thumbHeightPx = with(density) { ThumbHeight.toPx() }

    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    // 拖动中由手势直接驱动的 thumb 顶边位置；非拖动时为 null，回退到 fraction 映射
    var draggedTopPx by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var recentlyScrolled by remember { mutableStateOf(false) }

    // 滚动停止后短暂保持 Active 透明度再回落到 Idle
    LaunchedEffect(isContentScrolling) {
        if (isContentScrolling) {
            recentlyScrolled = true
        } else {
            delay(FADE_OUT_DELAY_MS)
            recentlyScrolled = false
        }
    }

    val thumbState = when {
        isDragging -> ScrollbarThumbState.Drag
        isContentScrolling || recentlyScrolled -> ScrollbarThumbState.Active
        else -> ScrollbarThumbState.Idle
    }
    val thumbAlpha by animateFloatAsState(
        targetValue = scrollbarThumbAlpha(thumbState),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scrollbarThumbAlpha"
    )
    val thumbWidthDp by animateFloatAsState(
        targetValue = if (isDragging) ThumbDragWidth.value else ThumbIdleWidth.value,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scrollbarThumbWidth"
    )

    Box(
        modifier = modifier
            .width(TouchWidth)
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val topPx = draggedTopPx ?: fractionToThumbOffsetPx(
                fractionState.value ?: return@Canvas,
                size.height,
                thumbHeightPx
            )
            val thumbWidthPx = thumbWidthDp.dp.toPx()
            drawRoundRect(
                color = thumbColor.copy(alpha = thumbAlpha),
                topLeft = Offset(x = size.width - thumbWidthPx, y = topPx),
                size = Size(thumbWidthPx, thumbHeightPx),
                cornerRadius = CornerRadius(thumbWidthPx / 2f)
            )
        }

        // thumb 热区：随 thumb 移动的小矩形，只在这里拦截触摸并排除系统手势
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val topPx = draggedTopPx ?: fractionToThumbOffsetPx(
                        fractionState.value ?: 0f,
                        trackHeightPx,
                        thumbHeightPx
                    )
                    IntOffset(0, topPx.roundToInt())
                }
                .width(TouchWidth)
                .height(ThumbHeight)
                .systemGestureExclusion()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        down.consume()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        isDragging = true
                        onDragStateChanged(true)
                        var topPx = draggedTopPx
                            ?: fractionToThumbOffsetPx(fractionState.value ?: 0f, trackHeightPx, ThumbHeight.toPx())
                        draggedTopPx = topPx
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) break
                                val maxTop = (trackHeightPx - ThumbHeight.toPx()).coerceAtLeast(0f)
                                topPx = (topPx + change.positionChange().y).coerceIn(0f, maxTop)
                                draggedTopPx = topPx
                                change.consume()
                                onDragFraction(thumbOffsetToFraction(topPx, trackHeightPx, ThumbHeight.toPx()))
                            }
                        } finally {
                            isDragging = false
                            draggedTopPx = null
                            onDragStateChanged(false)
                        }
                    }
                }
        )
    }
}

/** 从 LazyListState 估算滚动比例；可见项为空（尚未完成布局）时视为隐藏 */
private fun estimateLazyListFraction(state: LazyListState, extraUnloadedItems: Int): Float? {
    val layoutInfo = state.layoutInfo
    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull() ?: return null
    return lazyListScrollFraction(
        totalItemsCount = layoutInfo.totalItemsCount,
        canScrollBackward = state.canScrollBackward,
        canScrollForward = state.canScrollForward,
        visibleSpan = estimateVisibleSpan(layoutInfo, state.firstVisibleItemScrollOffset),
        firstVisibleItemIndex = firstVisible.index,
        firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
        firstVisibleItemSize = firstVisible.size,
        extraUnloadedItems = extraUnloadedItems
    )
}

/** 视口占用的连续 item 数；末项露出高度按视口边界裁剪（不等高 item 的局部近似） */
private fun estimateVisibleSpan(layoutInfo: LazyListLayoutInfo, firstVisibleItemScrollOffset: Int): Float {
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return 0f
    val first = visibleItems.first()
    val last = visibleItems.last()
    val lastVisibleSize = (
        minOf(last.offset + last.size, layoutInfo.viewportEndOffset) -
            maxOf(last.offset, layoutInfo.viewportStartOffset)
        ).coerceAtLeast(0)
    return lazyListVisibleSpan(
        firstVisibleItemIndex = first.index,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        firstVisibleItemSize = first.size,
        lastVisibleItemIndex = last.index,
        lastVisibleItemVisibleSize = lastVisibleSize,
        lastVisibleItemSize = last.size
    )
}
