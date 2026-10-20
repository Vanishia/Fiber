package com.bird.fiber.domain.markdown

/** 原文是唯一持久化数据；身份包含加载代次，重新打开同一 URI 也不会复用旧结果。 */
data class DocumentSnapshot(
    val fileUri: String?,
    val generation: Long,
    val revision: Long,
    val content: String
)

data class SourceRange(val start: Int, val endExclusive: Int)

/** 当前 CommonMark 不提供节点源码范围，未知范围明确留空，不能用显示偏移冒充。 */
data class MarkdownBlock(val type: String, val sourceRange: SourceRange? = null)
