package com.bird.fiber.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * MarkdownUtils 单元测试
 */
class MarkdownUtilsTest {

    // ==================== preprocessMarkdownForHardBreaks 测试 ====================

    @Test
    fun preprocessMarkdownForHardBreaks_normalLines_addsTwoSpaces() {
        // Arrange
        val content = "第一行\n第二行\n第三行"

        // Act
        val result = MarkdownUtils.preprocessMarkdownForHardBreaks(content)

        // Assert
        assertEquals("第一行  \n第二行  \n第三行  ", result)
    }

    @Test
    fun preprocessMarkdownForHardBreaks_emptyLines_notModified() {
        // Arrange
        val content = "第一行\n\n第二行"

        // Act
        val result = MarkdownUtils.preprocessMarkdownForHardBreaks(content)

        // Assert
        assertEquals("第一行  \n\n第二行  ", result)
    }

    @Test
    fun preprocessMarkdownForHardBreaks_blankLines_notModified() {
        // Arrange
        val content = "第一行\n   \n第二行"

        // Act
        val result = MarkdownUtils.preprocessMarkdownForHardBreaks(content)

        // Assert
        assertEquals("第一行  \n   \n第二行  ", result)
    }

    @Test
    fun preprocessMarkdownForHardBreaks_emptyString_returnsEmpty() {
        // Arrange
        val content = ""

        // Act
        val result = MarkdownUtils.preprocessMarkdownForHardBreaks(content)

        // Assert
        assertEquals("", result)
    }

    @Test
    fun preprocessMarkdownForHardBreaks_singleLine_addsTwoSpaces() {
        // Arrange
        val content = "单行内容"

        // Act
        val result = MarkdownUtils.preprocessMarkdownForHardBreaks(content)

        // Assert
        assertEquals("单行内容  ", result)
    }

    @Test
    fun preprocessMarkdownForHardBreaks_preservesLineEndings() {
        // Arrange
        val content = "第一行\r\n第二行"

        // Act
        val result = MarkdownUtils.preprocessMarkdownForHardBreaks(content)

        // Assert - lines() 方法会处理 \r\n 为 \n
        assertTrue(result.contains("第一行  "))
        assertTrue(result.contains("第二行  "))
    }

    // ==================== extractPlainTextPreview 测试 ====================

    @Test
    fun extractPlainTextPreview_removesCodeBlocks() {
        // Arrange
        val content = """
            这是正文
            ```kotlin
            fun main() {
                println("Hello")
            }
            ```
            这是后续
        """.trimIndent()

        // Act
        val result = MarkdownUtils.extractPlainTextPreview(content)

        // Assert
        assertFalse("应该移除代码块", result.contains("```"))
        assertFalse("应该移除代码块内容", result.contains("fun main"))
        assertTrue("应该保留正文", result.contains("这是正文"))
        assertTrue("应该保留后续", result.contains("这是后续"))
    }

    @Test
    fun extractPlainTextPreview_removesInlineCode() {
        // Arrange
        val content = "使用 `println()` 函数输出"

        // Act
        val result = MarkdownUtils.extractPlainTextPreview(content)

        // Assert
        assertFalse("应该移除反引号", result.contains("`"))
        assertTrue("应该保留文本", result.contains("println"))
    }

    @Test
    fun extractPlainTextPreview_removesLinksKeepsText() {
        // Arrange
        val content = "点击 [这里](https://example.com) 访问"

        // Act
        val result = MarkdownUtils.extractPlainTextPreview(content)

        // Assert
        assertFalse("应该移除链接语法", result.contains("["))
        assertFalse("应该移除链接语法", result.contains("]"))
        assertTrue("应该保留链接文本", result.contains("这里"))
    }

    @Test
    fun extractPlainTextPreview_removesImages() {
        // Arrange
        val content = "![图片说明](image.png) 正文内容"

        // Act
        val result = MarkdownUtils.extractPlainTextPreview(content)

        // Assert
        assertFalse("应该移除图片语法", result.contains("!["))
        assertTrue("应该保留正文", result.contains("正文内容"))
    }

    @Test
    fun extractPlainTextPreview_removesHeaders() {
        // Arrange
        val content = "# 一级标题\n## 二级标题\n正文"

        // Act
        val result = MarkdownUtils.extractPlainTextPreview(content)

        // Assert
        assertFalse("应该移除 # 标记", result.contains("#"))
        assertTrue("应该保留标题文本", result.contains("一级标题"))
        assertTrue("应该保留标题文本", result.contains("二级标题"))
    }

    @Test
    fun extractPlainTextPreview_removesBoldAndItalic() {
        // Arrange
        val content = "**粗体** __粗体2__ *斜体* _斜体2_"

        // Act
        val result = MarkdownUtils.extractPlainTextPreview(content)

        // Assert
        assertFalse("应该移除 **", result.contains("**"))
        assertFalse("应该移除 __", result.contains("__"))
        assertTrue("应该保留粗体文本", result.contains("粗体"))
        assertTrue("应该保留斜体文本", result.contains("斜体"))
    }

    @Test
    fun extractPlainTextPreview_removesListMarkers() {
        // Arrange
        val content = """
            - 项目1
            * 项目2
            + 项目3
            1. 项目4
        """.trimIndent()

        // Act
        val result = MarkdownUtils.extractPlainTextPreview(content)

        // Assert
        assertFalse("应该移除 - 标记", result.contains("- "))
        assertTrue("应该保留列表项文本", result.contains("项目1"))
        assertTrue("应该保留列表项文本", result.contains("项目4"))
    }

    @Test
    fun extractPlainTextPreview_truncatesLongContent() {
        // Arrange
        val content = "a".repeat(300)

        // Act
        val result = MarkdownUtils.extractPlainTextPreview(content, maxLength = 200)

        // Assert
        assertEquals("应该截断到指定长度加省略号", 203, result.length)
        assertTrue("应该以省略号结尾", result.endsWith("..."))
    }

    @Test
    fun extractPlainTextPreview_emptyString_returnsEmpty() {
        // Arrange
        val content = ""

        // Act
        val result = MarkdownUtils.extractPlainTextPreview(content)

        // Assert
        assertEquals("", result)
    }

    @Test
    fun extractPlainTextPreview_whitespaceOnly_returnsEmpty() {
        // Arrange
        val content = "   \n   \n   "

        // Act
        val result = MarkdownUtils.extractPlainTextPreview(content)

        // Assert
        assertEquals("", result)
    }
}
