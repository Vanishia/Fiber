package com.bird.fiber.utils

/**
 * Markdown 工具类
 *
 * 提供 Markdown 文本处理相关功能
 */
object MarkdownUtils {

    /**
     * 预处理 Markdown 文本，开启硬换行模式
     *
     * 硬换行规则：单个换行符也视为换行（而不是空格）
     * 实现：在每个非空行末尾添加两个空格（Markdown 的硬换行语法）
     *
     * @param content 原始 Markdown 内容
     * @return 处理后的内容，支持硬换行
     */
    fun preprocessMarkdownForHardBreaks(content: String): String {
        return content.lines().joinToString("\n") { line ->
            // 如果不是空行，在末尾添加两个空格
            if (line.isNotBlank()) {
                "$line  "
            } else {
                line
            }
        }
    }

    /**
     * 获取 Markdown 预览摘要
     *
     * 从 Markdown 内容中提取纯文本摘要，移除 Markdown 标记
     *
     * @param content Markdown 内容
     * @param maxLength 摘要最大长度
     * @return 纯文本摘要
     */
    fun extractPlainTextPreview(content: String, maxLength: Int = 200): String {
        // 移除代码块
        var text = content.replace(Regex("""```[\s\S]*?```"""), "")

        // 行内代码：移除反引号但保留内容（例如 `println()` -> println()）
        text = text.replace(Regex("""`([^`]*)`"""), "$1")

        // 移除链接，保留文本
        text = text.replace(Regex("""\[([^\]]*)\]\([^\)]*\)"""), "$1")
        // 移除图片
        text = text.replace(Regex("""!\[[^\]]*\]\([^\)]*\)"""), "")

        // 移除标题标记（按行处理）
        text = text.replace(Regex("""(?m)^#{1,6}\s*"""), "")

        // 移除粗体和斜体标记
        text = text.replace(Regex("""\*\*|__"""), "")
        text = text.replace(Regex("""\*|_"""), "")

        // 移除列表标记（按行处理）
        text = text.replace(Regex("""(?m)^[\s]*[-*+][\s]"""), "")
        text = text.replace(Regex("""(?m)^[\s]*\d+\.[\s]"""), "")

        // 移除 HTML 标签
        text = text.replace(Regex("""<[^>]*>"""), "")

        // 移除多余空白
        text = text.replace(Regex("""\s+"""), " ").trim()

        return if (text.length > maxLength) {
            text.take(maxLength) + "..."
        } else {
            text
        }
    }
}
