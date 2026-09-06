package com.bird.fiber.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraggableScrollbarMathTest {

    // ---- scrollFraction / scrollTargetForFraction ----

    @Test
    fun scrollFraction_returnsNullWhenNotScrollable() {
        assertNull(scrollFraction(0, 0))
        assertNull(scrollFraction(10, 0))
        assertNull(scrollFraction(0, -5))
    }

    @Test
    fun scrollFraction_isExactAtBoundaries() {
        assertEquals(0f, scrollFraction(0, 100)!!, 0f)
        assertEquals(1f, scrollFraction(100, 100)!!, 0f)
        assertEquals(0.5f, scrollFraction(50, 100)!!, 1e-6f)
    }

    @Test
    fun scrollFraction_clampsOutOfRangeValues() {
        assertEquals(1f, scrollFraction(150, 100)!!, 0f)
        assertEquals(0f, scrollFraction(-10, 100)!!, 0f)
    }

    @Test
    fun scrollTarget_mapsEndsExactly() {
        assertEquals(0, scrollTargetForFraction(0f, 1000))
        assertEquals(1000, scrollTargetForFraction(1f, 1000))
        assertEquals(500, scrollTargetForFraction(0.5f, 1000))
        assertEquals(0, scrollTargetForFraction(1f, 0))
    }

    @Test
    fun scrollTarget_clampsFraction() {
        assertEquals(1000, scrollTargetForFraction(1.5f, 1000))
        assertEquals(0, scrollTargetForFraction(-0.5f, 1000))
    }

    @Test
    fun scrollStateMapping_roundTripsWithinOnePixel() {
        val max = 9050
        for (f in listOf(0f, 0.13f, 0.4475f, 0.99f, 1f)) {
            val back = scrollFraction(scrollTargetForFraction(f, max), max)!!
            assertEquals(f, back, 0.001f)
        }
    }

    // ---- lazyListVisibleSpan ----

    @Test
    fun visibleSpan_countsPartialItemsInPixelUnits() {
        // 首项滚过一半(50/100)，末项露出 1/4(25/100)：span = (9+0.25) − (0+0.5) = 8.75
        val span = lazyListVisibleSpan(
            firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50, firstVisibleItemSize = 100,
            lastVisibleItemIndex = 9, lastVisibleItemVisibleSize = 25, lastVisibleItemSize = 100
        )
        assertEquals(8.75f, span, 1e-6f)
    }

    @Test
    fun visibleSpan_ignoresZeroSizedItems() {
        val span = lazyListVisibleSpan(
            firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50, firstVisibleItemSize = 0,
            lastVisibleItemIndex = 9, lastVisibleItemVisibleSize = 25, lastVisibleItemSize = 0
        )
        assertEquals(9f, span, 1e-6f)
    }

    // ---- lazyListScrollFraction ----

    @Test
    fun lazyFraction_hiddenWhenContentFitsViewport() {
        // 两端都不可滚 = 不足一屏
        assertNull(
            lazyListScrollFraction(
                totalItemsCount = 5, canScrollBackward = false, canScrollForward = false,
                visibleSpan = 5f,
                firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, firstVisibleItemSize = 100
            )
        )
        assertNull(
            lazyListScrollFraction(
                totalItemsCount = 0, canScrollBackward = false, canScrollForward = false,
                visibleSpan = 0f,
                firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, firstVisibleItemSize = 100
            )
        )
    }

    @Test
    fun lazyFraction_pinnedAtBoundaries() {
        // 钉死优先于估算：即使可见项信息有误差也精确到端点
        assertEquals(
            0f,
            lazyListScrollFraction(
                totalItemsCount = 100, canScrollBackward = false, canScrollForward = true,
                visibleSpan = 9.3f,
                firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 37, firstVisibleItemSize = 100
            )!!,
            0f
        )
        assertEquals(
            1f,
            lazyListScrollFraction(
                totalItemsCount = 100, canScrollBackward = true, canScrollForward = false,
                visibleSpan = 9.8f,
                firstVisibleItemIndex = 90, firstVisibleItemScrollOffset = 11, firstVisibleItemSize = 100
            )!!,
            0f
        )
    }

    @Test
    fun lazyFraction_matchesExactValueForUniformItems() {
        // 100 项 × 100px，视口 950px（首项滚过 50px，末项完整可见）
        // 精确 fraction = scrollY / maxScroll = 4050 / 9050
        val span = lazyListVisibleSpan(40, 50, 100, 49, 100, 100)
        val f = lazyListScrollFraction(
            totalItemsCount = 100, canScrollBackward = true, canScrollForward = true,
            visibleSpan = span,
            firstVisibleItemIndex = 40, firstVisibleItemScrollOffset = 50, firstVisibleItemSize = 100
        )!!
        assertEquals(4050f / 9050f, f, 1e-4f)
    }

    @Test
    fun lazyFraction_increasesAsFirstItemAdvances() {
        fun fractionAt(firstIndex: Int, offset: Int) = lazyListScrollFraction(
            totalItemsCount = 100, canScrollBackward = true, canScrollForward = true,
            visibleSpan = 9.5f,
            firstVisibleItemIndex = firstIndex,
            firstVisibleItemScrollOffset = offset,
            firstVisibleItemSize = 100
        )!!
        assertTrue(fractionAt(0, 50) < fractionAt(40, 0))
        assertTrue(fractionAt(40, 0) < fractionAt(40, 50))
        assertTrue(fractionAt(40, 50) < fractionAt(90, 0))
    }

    @Test
    fun lazyFraction_usesPixelOffsetRelativeToFirstItemSize() {
        // 不等高 item：同样滚过 50px，首项高 100px 时进度 0.5 项，高 200px 时 0.25 项
        val smallItem = lazyListScrollFraction(
            totalItemsCount = 100, canScrollBackward = true, canScrollForward = true,
            visibleSpan = 9.5f,
            firstVisibleItemIndex = 40, firstVisibleItemScrollOffset = 50, firstVisibleItemSize = 100
        )!!
        val largeItem = lazyListScrollFraction(
            totalItemsCount = 100, canScrollBackward = true, canScrollForward = true,
            visibleSpan = 9.5f,
            firstVisibleItemIndex = 40, firstVisibleItemScrollOffset = 50, firstVisibleItemSize = 200
        )!!
        assertTrue(largeItem < smallItem)
    }

    // ---- extraUnloadedItems（Paging 分页未完时的虚拟余量） ----

    @Test
    fun lazyFraction_reserveKeepsThumbOffBottomWhilePagesRemain() {
        // 已到已加载末尾（canScrollForward=false），但分页未完：
        // 不钉 1f，按虚拟总量估算 = 35 / (40+20−5)
        val f = lazyListScrollFraction(
            totalItemsCount = 40, canScrollBackward = true, canScrollForward = false,
            visibleSpan = 5f,
            firstVisibleItemIndex = 35, firstVisibleItemScrollOffset = 0, firstVisibleItemSize = 100,
            extraUnloadedItems = 20
        )!!
        assertEquals(35f / 55f, f, 1e-4f)
        assertTrue(f < 1f)
    }

    @Test
    fun lazyFraction_withoutReserveStillPinsBottomExactly() {
        // 分页已加载完（余量为 0）：维持钉底语义，拖到底就是真底
        assertEquals(
            1f,
            lazyListScrollFraction(
                totalItemsCount = 40, canScrollBackward = true, canScrollForward = false,
                visibleSpan = 5f,
                firstVisibleItemIndex = 35, firstVisibleItemScrollOffset = 0, firstVisibleItemSize = 100,
                extraUnloadedItems = 0
            )!!,
            0f
        )
    }

    @Test
    fun lazyFraction_reserveDampsJumpWhenPageAppends() {
        // 同一位置（已加载 40 项的末尾）追加一页后：无预留时 1f → 0.64（跳 0.36），
        // 有预留时 0.64 → 0.47（跳 0.17），回跳幅度同比压缩
        fun at(loaded: Int, extra: Int) = lazyListScrollFraction(
            totalItemsCount = loaded, canScrollBackward = true, canScrollForward = false,
            visibleSpan = 5f,
            firstVisibleItemIndex = loaded - 5, firstVisibleItemScrollOffset = 0, firstVisibleItemSize = 100,
            extraUnloadedItems = extra
        )!!
        val jumpWithoutReserve = 1f - at(60, 0)
        val jumpWithReserve = at(40, 20) - at(60, 20)
        assertTrue(jumpWithReserve < jumpWithoutReserve)
    }

    // ---- lazyListScrollTarget ----

    @Test
    fun lazyTarget_mapsFractionToIndexAndOffset() {
        // 100 项，视口 9.5 项：fraction 0.5 → 连续位置 45.25 → 第 45 项 + 0.25×100px
        val (index, offset) = lazyListScrollTarget(0.5f, totalItemsCount = 100, visibleSpan = 9.5f, referenceItemSizePx = 100)
        assertEquals(45, index)
        assertEquals(25, offset)
    }

    @Test
    fun lazyTarget_clampsIndexToLoadedRange() {
        // fraction=1：连续位置 90.5 → index 90，不越界
        val (index, offset) = lazyListScrollTarget(1f, totalItemsCount = 100, visibleSpan = 9.5f, referenceItemSizePx = 100)
        assertEquals(90, index)
        assertEquals(50, offset)
        // 拖动中 Paging 快照 itemCount 变小（span > count）也不越界
        assertEquals(
            0 to 0,
            lazyListScrollTarget(1f, totalItemsCount = 5, visibleSpan = 9.5f, referenceItemSizePx = 100)
        )
        assertEquals(
            0 to 0,
            lazyListScrollTarget(1.5f, totalItemsCount = 0, visibleSpan = 9.5f, referenceItemSizePx = 100)
        )
    }

    @Test
    fun lazyTarget_lastItemNeverCarriesOffset() {
        // span=1 时 fraction=1 → 连续位置 99 → 钉在末项开头，由列表吸附真实底部
        val (index, offset) = lazyListScrollTarget(1f, totalItemsCount = 100, visibleSpan = 1f, referenceItemSizePx = 100)
        assertEquals(99, index)
        assertEquals(0, offset)
    }

    @Test
    fun lazyMapping_roundTripsForUniformItems() {
        val span = 9.5f
        for (f in listOf(0.1f, 0.4475f, 0.9f)) {
            val (index, offset) = lazyListScrollTarget(f, totalItemsCount = 100, visibleSpan = span, referenceItemSizePx = 100)
            val back = lazyListScrollFraction(
                totalItemsCount = 100, canScrollBackward = true, canScrollForward = true,
                visibleSpan = span,
                firstVisibleItemIndex = index,
                firstVisibleItemScrollOffset = offset,
                firstVisibleItemSize = 100
            )!!
            assertEquals(f, back, 0.001f)
        }
    }

    @Test
    fun lazyTarget_reserveClampsToLoadedRangeAndRoundTrips() {
        // 分页未完（余量 20）：fraction=1 拖到底仍钳到已加载末项，不越界
        val (index, offset) = lazyListScrollTarget(
            1f, totalItemsCount = 40, visibleSpan = 5f, referenceItemSizePx = 100,
            extraUnloadedItems = 20
        )
        assertEquals(39, index)
        assertEquals(0, offset)
        // 已加载末尾的显示比例（cap ≈ 35/55）往返一致（浮点容差，不钉死具体 index）
        val cap = 35f / 55f
        val (capIndex, capOffset) = lazyListScrollTarget(
            cap, totalItemsCount = 40, visibleSpan = 5f, referenceItemSizePx = 100,
            extraUnloadedItems = 20
        )
        val backCap = lazyListScrollFraction(
            totalItemsCount = 40, canScrollBackward = true, canScrollForward = true,
            visibleSpan = 5f,
            firstVisibleItemIndex = capIndex, firstVisibleItemScrollOffset = capOffset,
            firstVisibleItemSize = 100, extraUnloadedItems = 20
        )!!
        assertEquals(cap, backCap, 0.001f)
        // 中间位置往返一致
        val (midIndex, midOffset) = lazyListScrollTarget(
            0.3f, totalItemsCount = 40, visibleSpan = 5f, referenceItemSizePx = 100,
            extraUnloadedItems = 20
        )
        val back = lazyListScrollFraction(
            totalItemsCount = 40, canScrollBackward = true, canScrollForward = true,
            visibleSpan = 5f,
            firstVisibleItemIndex = midIndex, firstVisibleItemScrollOffset = midOffset,
            firstVisibleItemSize = 100, extraUnloadedItems = 20
        )!!
        assertEquals(0.3f, back, 0.001f)
    }

    // ---- thumb 偏移映射 ----

    @Test
    fun thumbOffset_mapsFractionEnds() {
        assertEquals(0f, fractionToThumbOffsetPx(0f, 1000f, 100f), 0f)
        assertEquals(900f, fractionToThumbOffsetPx(1f, 1000f, 100f), 0f)
        assertEquals(450f, fractionToThumbOffsetPx(0.5f, 1000f, 100f), 0f)
    }

    @Test
    fun thumbOffset_roundTrips() {
        for (f in listOf(0f, 0.33f, 1f)) {
            val offset = fractionToThumbOffsetPx(f, 777f, 100f)
            assertEquals(f, thumbOffsetToFraction(offset, 777f, 100f), 1e-5f)
        }
    }

    @Test
    fun thumbOffset_handlesTrackShorterThanThumb() {
        assertEquals(0f, fractionToThumbOffsetPx(0.7f, 50f, 100f), 0f)
        assertEquals(0f, thumbOffsetToFraction(30f, 50f, 100f), 0f)
    }

    @Test
    fun thumbOffset_clampsOutOfRange() {
        assertEquals(900f, fractionToThumbOffsetPx(2f, 1000f, 100f), 0f)
        assertEquals(1f, thumbOffsetToFraction(5000f, 1000f, 100f), 0f)
        assertEquals(0f, thumbOffsetToFraction(-30f, 1000f, 100f), 0f)
    }

    // ---- thumb 透明度策略 ----

    @Test
    fun thumbAlpha_matchesSpec() {
        assertEquals(0.12f, scrollbarThumbAlpha(ScrollbarThumbState.Idle), 0f)
        assertEquals(0.25f, scrollbarThumbAlpha(ScrollbarThumbState.Active), 0f)
        assertEquals(0.55f, scrollbarThumbAlpha(ScrollbarThumbState.Drag), 0f)
    }
}
