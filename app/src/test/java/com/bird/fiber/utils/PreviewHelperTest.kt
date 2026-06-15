package com.bird.fiber.utils

import android.content.ContentResolver
import android.net.Uri
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * PreviewHelper 单元测试
 *
 * 测试预览生成的核心功能：
 * - 从文件 URI 读取前 N 字符
 * - 从完整内容取前 N 字符
 * - PreviewHelper 不加任何截断标记，视觉截断由 UI 层处理
 */
class PreviewHelperTest {

    private lateinit var mockContentResolver: ContentResolver

    @Before
    fun setup() {
        mockkObject(timber.log.Timber)
        every { timber.log.Timber.e(any<Throwable>(), any<String>(), *anyVararg()) } just Runs

        mockContentResolver = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== readFilePreview 测试 ====================

    @Test
    fun readFilePreview_shortContent_showsAll() {
        val content = "短内容"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val uri = mockk<Uri>()
        every { mockContentResolver.openInputStream(uri) } returns inputStream

        val result = PreviewHelper.readFilePreview(mockContentResolver, uri, maxChars = 100)

        assertEquals("短内容", result)
    }

    @Test
    fun readFilePreview_longContent_truncatesToMaxChars() {
        val content = "a".repeat(500)
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val uri = mockk<Uri>()
        every { mockContentResolver.openInputStream(uri) } returns inputStream

        val result = PreviewHelper.readFilePreview(mockContentResolver, uri, maxChars = 100)

        assertEquals(100, result.length)
    }

    @Test
    fun readFilePreview_emptyFile_returnsEmpty() {
        val inputStream = ByteArrayInputStream("".toByteArray())
        val uri = mockk<Uri>()
        every { mockContentResolver.openInputStream(uri) } returns inputStream

        val result = PreviewHelper.readFilePreview(mockContentResolver, uri)

        assertEquals("", result)
    }

    @Test
    fun readFilePreview_nullInputStream_returnsEmpty() {
        val uri = mockk<Uri>()
        every { mockContentResolver.openInputStream(uri) } returns null

        val result = PreviewHelper.readFilePreview(mockContentResolver, uri)

        assertEquals("", result)
    }

    @Test
    fun readFilePreview_exception_returnsEmpty() {
        val uri = mockk<Uri>()
        every { mockContentResolver.openInputStream(uri) } throws Exception("读取失败")

        val result = PreviewHelper.readFilePreview(mockContentResolver, uri)

        assertEquals("", result)
        verify { timber.log.Timber.e(any<Throwable>(), any<String>()) }
    }

    @Test
    fun readFilePreview_customMaxChars_respectsLimit() {
        val content = "第一行\n第二行\n第三行\n第四行\n第五行"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val uri = mockk<Uri>()
        every { mockContentResolver.openInputStream(uri) } returns inputStream

        val result = PreviewHelper.readFilePreview(mockContentResolver, uri, maxChars = 5)

        assertEquals(5, result.length)
    }

    @Test
    fun readFilePreview_exactMaxChars_showsAll() {
        val content = "内容正好十二个字符"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val uri = mockk<Uri>()
        every { mockContentResolver.openInputStream(uri) } returns inputStream

        val result = PreviewHelper.readFilePreview(mockContentResolver, uri, maxChars = 12)

        assertEquals("内容正好十二个字符", result)
    }

    @Test
    fun readFilePreview_whitespacePrefix_stripped() {
        val content = "   \n  带缩进的内容"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val uri = mockk<Uri>()
        every { mockContentResolver.openInputStream(uri) } returns inputStream

        val result = PreviewHelper.readFilePreview(mockContentResolver, uri, maxChars = 100)

        assertEquals("带缩进的内容", result)
    }

    @Test
    fun readFilePreview_multipleLines_preservesStructure() {
        val content = "第一行\n第二行\n第三行"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val uri = mockk<Uri>()
        every { mockContentResolver.openInputStream(uri) } returns inputStream

        val result = PreviewHelper.readFilePreview(mockContentResolver, uri, maxChars = 100)

        assertEquals("第一行\n第二行\n第三行", result)
    }

    // ==================== generatePreview 测试 ====================

    @Test
    fun generatePreview_shortContent_showsAll() {
        val content = "一段短内容"

        val result = PreviewHelper.generatePreview(content, maxChars = 100)

        assertEquals("一段短内容", result)
    }

    @Test
    fun generatePreview_longContent_truncatesToMaxChars() {
        val content = "a".repeat(1000)

        val result = PreviewHelper.generatePreview(content, maxChars = 100)

        assertEquals(100, result.length)
    }

    @Test
    fun generatePreview_exactMaxChars_showsAll() {
        val content = "1234567890"

        val result = PreviewHelper.generatePreview(content, maxChars = 10)

        assertEquals("1234567890", result)
    }

    @Test
    fun generatePreview_emptyContent_returnsEmpty() {
        val content = ""

        val result = PreviewHelper.generatePreview(content)

        assertEquals("", result)
    }

    @Test
    fun generatePreview_whitespaceOnly_returnsEmpty() {
        val content = "   \n   \n   "

        val result = PreviewHelper.generatePreview(content)

        assertEquals("", result)
    }

    @Test
    fun generatePreview_singleLine_returnsSingleLine() {
        val content = "只有一行"

        val result = PreviewHelper.generatePreview(content)

        assertEquals("只有一行", result)
    }

    @Test
    fun generatePreview_customMaxChars_respectsLimit() {
        val content = "第一行\n第二行\n第三行\n第四行\n第五行"

        val result = PreviewHelper.generatePreview(content, maxChars = 5)

        assertEquals(5, result.length)
    }

    @Test
    fun generatePreview_mixedContent_handlesCorrectly() {
        val content = "# 标题\n\n这是正文内容\n\n- 列表项1\n- 列表项2"

        val result = PreviewHelper.generatePreview(content, maxChars = 200)

        assertTrue("应包含标题", result.contains("# 标题"))
        assertTrue("应包含正文", result.contains("这是正文内容"))
    }

    @Test
    fun generatePreview_noArtificialTruncationMark() {
        // PreviewHelper 不应添加任何 "…" 截断标记
        val content = "a".repeat(1000)

        val result = PreviewHelper.generatePreview(content, maxChars = 100)

        assertFalse("不应包含截断标记", result.contains("…"))
        assertFalse("不应包含截断标记", result.contains("..."))
    }

    @Test
    fun generatePreview_leadingWhitespace_stripped() {
        val content = "\n\n\n实际内容从第四行开始"

        val result = PreviewHelper.generatePreview(content, maxChars = 200)

        assertFalse("前导空白应被移除", result.startsWith("\n"))
        assertEquals("实际内容从第四行开始", result)
    }

    @Test
    fun generatePreview_veryLongParagraph_noArtificialBoundary() {
        // 一段超长的连续文字（无句号、无空格），不应该被中间截断加标记
        val content = "这是一段非常长的没有换行没有标点的连续文字".repeat(50)

        val result = PreviewHelper.generatePreview(content, maxChars = 80)

        // 应该直接取前 80 字符，不做边界搜索，不加 "…"
        assertEquals(80, result.length)
        assertFalse("不应包含截断标记", result.contains("…"))
    }
}
