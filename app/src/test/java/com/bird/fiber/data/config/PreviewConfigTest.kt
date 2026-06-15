package com.bird.fiber.data.config

import org.junit.Assert.*
import org.junit.Test

/**
 * PreviewConfig 配置测试
 *
 * 测试预览配置常量：
 * - 字符数限制
 * - 行数限制
 * - 缓存大小
 */
class PreviewConfigTest {

    @Test
    fun maxChars_isPositive() {
        assertTrue(PreviewConfig.MAX_CHARS > 0)
    }

    @Test
    fun maxChars_isReasonableValue() {
        // 预览最大字符数应在合理范围内（100-500字符）
        assertTrue(PreviewConfig.MAX_CHARS in 100..500)
    }

    @Test
    fun maxLines_isPositive() {
        assertTrue(PreviewConfig.MAX_LINES > 0)
    }

    @Test
    fun maxLines_isReasonableValue() {
        // 预览行数应该在合理范围内（2-6行）
        assertTrue(PreviewConfig.MAX_LINES in 2..6)
    }

    @Test
    fun recentPreviewLimit_isPositive() {
        assertTrue(PreviewConfig.RECENT_PREVIEW_LIMIT > 0)
    }

    @Test
    fun recentPreviewLimit_isReasonableValue() {
        // 预加载数量应该在合理范围内（10-50）
        assertTrue(PreviewConfig.RECENT_PREVIEW_LIMIT in 10..50)
    }

    @Test
    fun maxChars_greaterThanMaxDisplayLines() {
        // 预览字符数应该大于 UI 显示行数能容纳的最小字符数
        // 手机屏幕每行约 20-30 个中文字符
        val minCharsFor3Lines = 3 * 20  // 3行 × 20字符
        assertTrue(PreviewConfig.MAX_CHARS > minCharsFor3Lines)
    }
}
