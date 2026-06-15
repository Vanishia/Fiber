package com.bird.fiber.data.local

import android.content.ContentResolver
import android.net.Uri
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * PreviewCache 单元测试
 *
 * 测试预览缓存的核心功能：
 * - 缓存读写
 * - 版本号更新
 * - 懒加载
 * - 清除缓存
 */
@ExperimentalCoroutinesApi
class PreviewCacheTest {

    private lateinit var previewCache: PreviewCache
    private lateinit var mockContentResolver: ContentResolver
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        // 设置测试调度器 - 使用 UnconfinedTestDispatcher 立即执行协程
        Dispatchers.setMain(testDispatcher)

        // Mock Timber - 使用 mockkObject 而不是 mockkStatic
        mockkObject(timber.log.Timber)
        every { timber.log.Timber.d(any<String>(), *anyVararg()) } just Runs
        every { timber.log.Timber.e(any<Throwable>(), any<String>(), *anyVararg()) } just Runs

        previewCache = PreviewCache()
        mockContentResolver = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ==================== getPreview 测试 ====================

    @Test
    fun getPreview_cacheEmpty_returnsNull() {
        // Act
        val result = previewCache.getPreview("test-uri")

        // Assert
        assertNull("空缓存应该返回 null", result)
    }

    @Test
    fun getPreview_cacheHasValue_returnsValue() {
        // Arrange
        previewCache.setPreview("test-uri", "预览内容")

        // Act
        val result = previewCache.getPreview("test-uri")

        // Assert
        assertEquals("预览内容", result)
    }

    // ==================== setPreview 测试 ====================

    @Test
    fun setPreview_newValue_updatesCache() {
        // Act
        previewCache.setPreview("test-uri", "新预览")

        // Assert
        assertEquals("新预览", previewCache.getPreview("test-uri"))
    }

    @Test
    fun setPreview_newValue_incrementsVersion() = runTest {
        // Arrange
        val initialVersion = previewCache.version.first()

        // Act
        previewCache.setPreview("test-uri", "预览1")
        val version1 = previewCache.version.first()

        previewCache.setPreview("test-uri-2", "预览2")
        val version2 = previewCache.version.first()

        // Assert
        assertTrue("版本号应该递增", version1 > initialVersion)
        assertTrue("版本号应该递增", version2 > version1)
    }

    @Test
    fun setPreview_sameValue_doesNotIncrementVersion() = runTest {
        // Arrange
        previewCache.setPreview("test-uri", "预览内容")
        val version1 = previewCache.version.first()

        // Act
        previewCache.setPreview("test-uri", "预览内容")  // 相同内容
        val version2 = previewCache.version.first()

        // Assert
        assertEquals("相同内容不应该更新版本号", version1, version2)
    }

    @Test
    fun setPreview_differentValue_incrementsVersion() = runTest {
        // Arrange
        previewCache.setPreview("test-uri", "旧预览")
        val version1 = previewCache.version.first()

        // Act
        previewCache.setPreview("test-uri", "新预览")  // 不同内容
        val version2 = previewCache.version.first()

        // Assert
        assertTrue("不同内容应该更新版本号", version2 > version1)
    }

    // ==================== clear 测试 ====================

    @Test
    fun clear_removesAllCache() {
        // Arrange
        previewCache.setPreview("uri1", "预览1")
        previewCache.setPreview("uri2", "预览2")

        // Act
        previewCache.clear()

        // Assert
        assertNull("缓存应该被清空", previewCache.getPreview("uri1"))
        assertNull("缓存应该被清空", previewCache.getPreview("uri2"))
    }

    @Test
    fun clear_resetsVersion() = runTest {
        // Arrange
        previewCache.setPreview("uri1", "预览1")
        assertTrue("版本号应该大于 0", previewCache.version.first() > 0)

        // Act
        previewCache.clear()

        // Assert
        assertEquals("版本号应该重置为 0", 0L, previewCache.version.first())
    }

    // ==================== remove 测试 ====================

    @Test
    fun remove_existingUri_removesFromCache() {
        // Arrange
        previewCache.setPreview("uri1", "预览1")
        previewCache.setPreview("uri2", "预览2")

        // Act
        previewCache.remove("uri1")

        // Assert
        assertNull("uri1 应该被移除", previewCache.getPreview("uri1"))
        assertNotNull("uri2 应该保留", previewCache.getPreview("uri2"))
    }

    @Test
    fun remove_nonExistingUri_doesNotCrash() {
        // Act & Assert (不应该抛出异常)
        previewCache.remove("non-existing-uri")
    }

    // ==================== 并发测试 ====================

    @Test
    fun setPreview_multipleCalls_allValuesStored() {
        // Act
        repeat(10) { i ->
            previewCache.setPreview("uri-$i", "预览-$i")
        }

        // Assert
        repeat(10) { i ->
            assertEquals("预览-$i", previewCache.getPreview("uri-$i"))
        }
    }

    @Test
    fun version_multipleUpdates_incrementsCorrectly() = runTest {
        // Arrange
        val initialVersion = previewCache.version.first()

        // Act
        repeat(5) { i ->
            previewCache.setPreview("uri-$i", "预览-$i")
        }

        // Assert
        val finalVersion = previewCache.version.first()
        assertEquals("版本号应该增加 5", initialVersion + 5, finalVersion)
    }

    // ==================== LRU 淘汰测试 ====================

    @Test
    fun setPreview_exceedsMaxSize_evictsOldestEntry() {
        // Arrange - 添加 101 个预览（超过 MAX_CACHE_SIZE = 100）
        repeat(101) { i ->
            previewCache.setPreview("uri-$i", "预览-$i")
        }

        // Assert
        // 第一个应该被淘汰
        assertNull("最早的缓存应该被淘汰", previewCache.getPreview("uri-0"))
        // 最后一个应该还在
        assertNotNull("最新的缓存应该保留", previewCache.getPreview("uri-100"))
        // 中间的应该还在
        assertNotNull("中间的缓存应该保留", previewCache.getPreview("uri-50"))
    }

    @Test
    fun getPreview_accessOrder_affectsEviction() {
        // Arrange - 添加 100 个预览
        repeat(100) { i ->
            previewCache.setPreview("uri-$i", "预览-$i")
        }

        // Act - 访问第一个，使其成为最近使用
        previewCache.getPreview("uri-0")

        // 添加一个新的，应该淘汰 uri-1 而不是 uri-0
        previewCache.setPreview("uri-100", "预览-100")

        // Assert
        assertNotNull("被访问过的缓存应该保留", previewCache.getPreview("uri-0"))
        assertNull("未被访问的最早缓存应该被淘汰", previewCache.getPreview("uri-1"))
        assertNotNull("新添加的缓存应该存在", previewCache.getPreview("uri-100"))
    }
}
