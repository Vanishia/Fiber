package com.bird.fiber.domain.markdown

import org.commonmark.node.Node

/** AST 仅供解析/渲染适配内部消费，不向 Compose 暴露可变节点。 */
class ParsedDocument internal constructor(
    val document: DocumentSnapshot,
    val blocks: List<MarkdownBlock>,
    internal val root: Node
)
