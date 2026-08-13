package com.bird.fiber.utils

/**
 * 将超大 Markdown 正文切分为可独立渲染的块。
 *
 * 只在「不处于代码块内的空行」处切分，避免把代码块、表格等跨行结构拦腰切断。
 * 找不到安全切点时会保留更大的块，不会在任意位置硬切。
 */
object MarkdownChunker {

    /**
     * 按 [maxChunkChars] 的目标大小切分 [content]。
     *
     * 内容不超过 [maxChunkChars] 时直接返回单块列表。
     */
    fun split(content: String, maxChunkChars: Int): List<String> {
        if (content.length <= maxChunkChars) return listOf(content)

        val chunks = mutableListOf<String>()
        var chunkStart = 0
        var lineStart = 0
        var lastSplitPoint = -1
        var openFence: Char? = null

        while (lineStart < content.length) {
            val newlineIndex = content.indexOf('\n', lineStart)
            val lineEnd = if (newlineIndex < 0) content.length else newlineIndex
            val nextLineStart = if (newlineIndex < 0) content.length else newlineIndex + 1
            val trimmed = content.substring(lineStart, lineEnd).trim()

            val fenceChar = fenceMarkerOf(trimmed)
            when {
                openFence == null && fenceChar != null -> openFence = fenceChar
                openFence != null && fenceChar == openFence -> openFence = null
            }

            if (openFence == null && trimmed.isEmpty()) {
                lastSplitPoint = nextLineStart
            }
            if (nextLineStart - chunkStart >= maxChunkChars && lastSplitPoint > chunkStart) {
                chunks += content.substring(chunkStart, lastSplitPoint)
                chunkStart = lastSplitPoint
            }
            lineStart = nextLineStart
        }

        if (chunkStart < content.length) {
            chunks += content.substring(chunkStart)
        }
        return chunks
    }

    /**
     * 识别围栏代码块标记行（``` 或 ~~~ 开头），返回围栏字符；非标记行返回 null
     */
    private fun fenceMarkerOf(trimmedLine: String): Char? {
        if (trimmedLine.length < 3) return null
        val first = trimmedLine[0]
        if (first != '`' && first != '~') return null
        return if (trimmedLine[1] == first && trimmedLine[2] == first) first else null
    }
}
