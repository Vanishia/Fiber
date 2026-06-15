package com.bird.fiber.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * FileUtils 的单元测试
 *
 * 测试原则：
 * 1. 给定输入
 * 2. 调用函数
 * 3. 检查输出是否符合预期
 */
class FileUtilsTest {

    // ==================== formatDate 测试 ====================

    @Test
    fun formatDate_formatCorrectly() {
        // Arrange（准备）：给一个固定的时间戳
        val timestamp = 1706745600000L  // 2024-01-31 12:00:00

        // Act（执行）：调用函数
        val result = FileUtils.formatDate(timestamp)

        // Assert（断言）：检查结果
        // 注意：这个测试可能会因为时区失败，这里只是示例
        assertTrue("结果应该包含日期", result.contains("2024"))
        assertTrue("结果应该包含时间", result.contains(":"))
    }

    @Test
    fun formatDate_timestampZero_returnsValidDate() {
        val result = FileUtils.formatDate(0L)

        // 0 时间戳应该返回 1970-01-01 的某个时间
        assertTrue("结果不应该为空", result.isNotEmpty())
        assertTrue("应该包含年份", result.contains("1970"))
    }

    // ==================== formatFileSize 测试 ====================

    @Test
    fun formatFileSize_bytes_returnsCorrectFormat() {
        // 测试小于 1KB 的文件
        val result = FileUtils.formatFileSize(512L)

        assertEquals("512 B", result)
    }

    @Test
    fun formatFileSize_kilobytes_returnsCorrectFormat() {
        // 测试 KB 范围的文件
        val result = FileUtils.formatFileSize(2048L)  // 2KB

        assertEquals("2 KB", result)
    }

    @Test
    fun formatFileSize_megabytes_returnsCorrectFormat() {
        // 测试 MB 范围的文件
        val result = FileUtils.formatFileSize(3 * 1024 * 1024)  // 3MB

        assertEquals("3 MB", result)
    }

    @Test
    fun formatFileSize_zero_returnsZeroBytes() {
        val result = FileUtils.formatFileSize(0L)

        assertEquals("0 B", result)
    }

    @Test
    fun formatFileSize_boundary1KB_returns1KB() {
        // 测试边界情况：正好 1KB
        val result = FileUtils.formatFileSize(1024L)

        assertEquals("1 KB", result)
    }

    @Test
    fun formatFileSize_boundary1MB_returns1MB() {
        // 测试边界情况：正好 1MB
        val result = FileUtils.formatFileSize(1024 * 1024)

        assertEquals("1 MB", result)
    }
}
