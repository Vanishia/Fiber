package com.bird.fiber.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownChunkerTest {

    @Test
    fun split_contentBelowThreshold_returnsSingleChunk() {
        val content = "第一段\n\n第二段\n"

        val chunks = MarkdownChunker.split(content, maxChunkChars = 1000)

        assertEquals(listOf(content), chunks)
    }

    @Test
    fun split_largeContent_preservesExactContent() {
        val content = buildLargeContent(paragraphs = 500)

        val chunks = MarkdownChunker.split(content, maxChunkChars = 2000)

        assertTrue(chunks.size > 1)
        assertEquals(content, chunks.joinToString(""))
    }

    @Test
    fun split_splitsOnlyAtBlankLines() {
        val content = buildLargeContent(paragraphs = 500)

        val chunks = MarkdownChunker.split(content, maxChunkChars = 2000)

        // 除最后一块外，每块都应以空行边界结束（即 \n\n 之后）
        chunks.dropLast(1).forEach { chunk ->
            assertTrue("块应在空行边界结束: ...${chunk.takeLast(20)}", chunk.endsWith("\n\n"))
        }
    }

    @Test
    fun split_doesNotSplitInsideFencedCodeBlock() {
        val codeBody = (1..200).joinToString("\n") { "代码行 $it\n\n含空行" }
        val content = "开头\n\n```kotlin\n$codeBody\n```\n\n" + buildLargeContent(paragraphs = 300)

        val chunks = MarkdownChunker.split(content, maxChunkChars = 1000)

        assertEquals(content, chunks.joinToString(""))
        chunks.forEach { chunk ->
            val fenceCount = chunk.split("\n").count { it.trim().startsWith("```") }
            assertEquals("每块内代码围栏应成对出现: ${chunk.take(30)}", 0, fenceCount % 2)
        }
    }

    @Test
    fun split_noSafeSplitPoint_returnsSingleChunk() {
        // 没有任何空行，超过阈值也不硬切
        val content = "很长的段落没有空行".repeat(500)

        val chunks = MarkdownChunker.split(content, maxChunkChars = 100)

        assertEquals(listOf(content), chunks)
    }

    @Test
    fun split_unclosedFence_keepsRestAsOneChunk() {
        val content = "第一段\n\n```\n未闭合的代码块\n\n" + "填充内容\n\n".repeat(300)

        val chunks = MarkdownChunker.split(content, maxChunkChars = 100)

        assertEquals(content, chunks.joinToString(""))
        assertEquals(2, chunks.size)
        assertEquals("第一段\n\n", chunks[0])
    }

    private fun buildLargeContent(paragraphs: Int): String {
        return (1..paragraphs).joinToString("\n\n") { "第 $it 段，包含一些用于填充的文本内容。" }
    }
}
