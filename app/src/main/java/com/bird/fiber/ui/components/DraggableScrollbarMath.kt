package com.bird.fiber.ui.components

import kotlin.math.roundToInt

/**
 * 可拖动滚动条的纯映射算法。
 *
 * 不依赖 Compose，保证纯 JVM 单测可覆盖（见 DraggableScrollbarMathTest）。
 * fraction 一律约定为 [0,1]；返回 null 表示"内容不足一屏，滚动条应隐藏"。
 */

/** thumb 视觉状态：闲时 / 滚动中（含刚停滚的短暂停留）/ 拖动中 */
enum class ScrollbarThumbState { Idle, Active, Drag }

/** 各视觉状态下的 thumb 透明度 */
fun scrollbarThumbAlpha(state: ScrollbarThumbState): Float = when (state) {
    ScrollbarThumbState.Idle -> 0.12f
    ScrollbarThumbState.Active -> 0.25f
    ScrollbarThumbState.Drag -> 0.55f
}

/**
 * 精确滚动比例（ScrollState 等直接暴露 value/max 的容器）。
 * max <= 0 表示内容不足一屏，返回 null（隐藏滚动条）。
 */
fun scrollFraction(value: Int, max: Int): Float? {
    if (max <= 0) return null
    return (value.toFloat() / max).coerceIn(0f, 1f)
}

/** [scrollFraction] 的反向映射：fraction → 目标滚动位置（px） */
fun scrollTargetForFraction(fraction: Float, max: Int): Int {
    if (max <= 0) return 0
    return (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
}

/**
 * 视口占用的"连续 item 数"（首项已滚过比例 → 末项已露出比例之间）。
 * item 高度不一致时按首/末项各自的像素尺寸换算成 item 单位，是局部线性近似。
 */
fun lazyListVisibleSpan(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSize: Int,
    lastVisibleItemIndex: Int,
    lastVisibleItemVisibleSize: Int,
    lastVisibleItemSize: Int
): Float {
    val firstProgress = firstVisibleItemIndex + itemProgress(firstVisibleItemScrollOffset, firstVisibleItemSize)
    val lastProgress = lastVisibleItemIndex + itemProgress(lastVisibleItemVisibleSize, lastVisibleItemSize)
    return (lastProgress - firstProgress).coerceAtLeast(0f)
}

/**
 * LazyList 滚动比例估算（像素级局部近似 + 边界钉死）。
 *
 * 边界钉死优先于估算：保证 thumb 能精确到达两端，不受估算误差影响。
 * 两端都不可滚 = 内容不足一屏，返回 null（隐藏滚动条）。
 *
 * [extraUnloadedItems] 为"已加载末尾之后仍有未加载内容"时预留的虚拟项数
 *（Paging 追加加载场景，调用方按页大小传入）。此时不再把已加载末尾钉死为 1f，
 * 分母计入虚拟余量，thumb 行程被压缩在轨道上部——同一位置在分页追加后的
 * fraction 变化量随之同比缩小，缓解向下滚动时 thumb 向上回跳。
 */
fun lazyListScrollFraction(
    totalItemsCount: Int,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    visibleSpan: Float,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSize: Int,
    extraUnloadedItems: Int = 0
): Float? {
    if (totalItemsCount <= 0) return null
    if (!canScrollForward && !canScrollBackward) return null
    if (!canScrollBackward) return 0f
    // 已确认没有更多内容时才钉底；分页未加载完时走下方估算（分母含虚拟余量，结果 < 1）
    if (!canScrollForward && extraUnloadedItems <= 0) return 1f
    val denominator = totalItemsCount + extraUnloadedItems - visibleSpan
    if (denominator <= 0f) return null
    val firstProgress = firstVisibleItemIndex + itemProgress(firstVisibleItemScrollOffset, firstVisibleItemSize)
    return (firstProgress / denominator).coerceIn(0f, 1f)
}

/**
 * [lazyListScrollFraction] 的反向映射：fraction → (itemIndex, scrollOffset)。
 *
 * index 强制 clamp 到 [0, totalItemsCount - 1]：Paging 场景 totalItemsCount 来自
 * 拖动时刻的快照，随后可能因分页加载/刷新变化，clamp 是防越界崩溃的防线
 *（调用侧发起 scrollToItem 前仍应再按当时的 layoutInfo.totalItemsCount 复核一次）。
 * 目标落在最后一项时 offset 归零：钉在末项开头，由列表自身吸附到真实底部。
 *
 * [extraUnloadedItems] 须与正向估算传入的值一致：往返使用同一虚拟总量，
 * 保证拖动位置与 thumb 显示位置互逆；index 依旧 clamp 到已加载范围。
 */
fun lazyListScrollTarget(
    fraction: Float,
    totalItemsCount: Int,
    visibleSpan: Float,
    referenceItemSizePx: Int,
    extraUnloadedItems: Int = 0
): Pair<Int, Int> {
    if (totalItemsCount <= 0) return 0 to 0
    val maxContinuous = (totalItemsCount + extraUnloadedItems - visibleSpan).coerceAtLeast(0f)
    val target = fraction.coerceIn(0f, 1f) * maxContinuous
    val index = target.toInt().coerceIn(0, totalItemsCount - 1)
    if (index >= totalItemsCount - 1) return (totalItemsCount - 1) to 0
    val offset = ((target - index).coerceIn(0f, 1f) * referenceItemSizePx).roundToInt().coerceAtLeast(0)
    return index to offset
}

/** fraction → thumb 顶边在轨道内的 px 偏移（轨道不足 thumb 高时恒 0，不除零） */
fun fractionToThumbOffsetPx(fraction: Float, trackHeightPx: Float, thumbHeightPx: Float): Float {
    val available = trackHeightPx - thumbHeightPx
    if (available <= 0f) return 0f
    return fraction.coerceIn(0f, 1f) * available
}

/** thumb 顶边 px 偏移 → fraction（[fractionToThumbOffsetPx] 的逆运算） */
fun thumbOffsetToFraction(offsetPx: Float, trackHeightPx: Float, thumbHeightPx: Float): Float {
    val available = trackHeightPx - thumbHeightPx
    if (available <= 0f) return 0f
    return (offsetPx / available).coerceIn(0f, 1f)
}

/** item 内进度：像素量 ÷ item 像素尺寸，clamp 到 [0,1]；尺寸为 0 时按 0 处理 */
private fun itemProgress(visiblePx: Int, sizePx: Int): Float =
    if (sizePx > 0) (visiblePx.toFloat() / sizePx).coerceIn(0f, 1f) else 0f
