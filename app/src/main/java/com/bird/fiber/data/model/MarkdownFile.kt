package com.bird.fiber.data.model

/**
 * Markdown 文件元数据（列表/搜索用）
 *
 * 只包含文件的元数据信息，不包含完整内容。
 * 用于文件列表展示和搜索，避免不必要的内容加载。
 *
 * @property uri 文件 SAF URI
 * @property name 文件名（不含后缀）
 * @property path 相对路径（用于显示文件夹结构）
 * @property lastModified 最后修改时间戳
 * @property size 文件大小（字节）
 * @property preview 内容预览（前几行，用于卡片展示）
 */
data class MarkdownFileMeta(
    val uri: String,
    val name: String,
    val path: String,
    val lastModified: Long,
    val size: Long,
    val preview: String = ""
)

/**
 * Markdown 文件完整内容（编辑器用）
 *
 * 包含文件的完整内容，用于编辑器读取和保存。
 *
 * @property uri 文件 SAF URI
 * @property name 文件名（不含后缀）
 * @property path 相对路径
 * @property lastModified 最后修改时间戳
 * @property size 文件大小（字节）
 * @property content 文件完整内容
 */
data class MarkdownFileContent(
    val uri: String,
    val name: String,
    val path: String,
    val lastModified: Long,
    val size: Long,
    val content: String
)

/**
 * Markdown 文件数据模型（向后兼容）
 *
 * ⚠️ 已弃用：请根据使用场景选择：
 * - 列表/搜索用：[MarkdownFileMeta]
 * - 编辑器用：[MarkdownFileContent]
 *
 * 同一个模型同时承载"元数据 + 可选 content + preview"会导致：
 * 1. 容易出现"某处误以为 content 已加载"的 bug
 * 2. 后续全文搜索/设置会进一步放大混用问题
 */
@Deprecated(
    message = "请根据使用场景选择 MarkdownFileMeta 或 MarkdownFileContent",
    replaceWith = ReplaceWith("MarkdownFileMeta"),
    level = DeprecationLevel.WARNING
)
data class MarkdownFile(
    val uri: String,
    val name: String,
    val path: String,
    val lastModified: Long,
    val size: Long,
    val content: String = "",
    val preview: String = ""
)

/**
 * 将 MarkdownFileMeta 转换为 MarkdownFileContent（添加内容）
 */
fun MarkdownFileMeta.withContent(content: String): MarkdownFileContent {
    return MarkdownFileContent(
        uri = uri,
        name = name,
        path = path,
        lastModified = lastModified,
        size = size,
        content = content
    )
}

/**
 * 将 MarkdownFileMeta 转换为旧的 MarkdownFile（向后兼容）
 */
fun MarkdownFileMeta.toMarkdownFile(): MarkdownFile {
    return MarkdownFile(
        uri = uri,
        name = name,
        path = path,
        lastModified = lastModified,
        size = size,
        content = "",
        preview = preview
    )
}

/**
 * 将 MarkdownFileContent 转换为旧的 MarkdownFile（向后兼容）
 */
fun MarkdownFileContent.toMarkdownFile(): MarkdownFile {
    return MarkdownFile(
        uri = uri,
        name = name,
        path = path,
        lastModified = lastModified,
        size = size,
        content = content,
        preview = ""
    )
}
