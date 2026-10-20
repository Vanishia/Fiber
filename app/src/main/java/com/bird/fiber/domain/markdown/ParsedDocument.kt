package com.bird.fiber.domain.markdown

import org.commonmark.node.Node

/** AST 仅供解析/渲染适配内部消费，不向 Compose 暴露可变节点。 */
class ParsedDocument internal constructor(
    val document: DocumentSnapshot,
    internal val root: Node
) {
    /** 预览本身不消费结构索引，按需构建，避免超大笔记额外常驻数万条描述。 */
    val blocks: List<MarkdownBlock> by lazy {
        generateSequence(root.firstChild) { it.next }
            .map { MarkdownBlock(it.javaClass.simpleName) }.toList()
    }
}
