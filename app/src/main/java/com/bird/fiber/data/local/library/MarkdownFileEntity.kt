package com.bird.fiber.data.local.library

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bird.fiber.data.model.MarkdownFileMeta

/**
 * Markdown 文件元数据实体
 *
 * 存储文件的元数据信息，用于加速列表加载和搜索
 *
 * 为什么需要这个表？
 * - SAF 每次都要扫描整个文件夹，性能差
 * - 数据库可以使用 LIMIT/OFFSET 进行真正的分页
 * - 支持 FTS5 全文搜索（未来）
 */
@Entity(
    tableName = "markdown_files",
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_id"],
            onDelete = ForeignKey.CASCADE  // 删除库时自动删除所有文件记录
        )
    ],
    indices = [
        Index(value = ["library_id"]),
        Index(value = ["last_modified"])
    ]
)
data class MarkdownFileEntity(
    /**
     * 文件 URI（主键）
     *
     * 格式: content://com.android.externalstorage.documents/document/primary%3Afiber%2Ftest.md
     */
    @PrimaryKey
    val uri: String,

    /**
     * 文件名（不含 .md 后缀）
     *
     * 例如: "我的笔记"
     */
    @ColumnInfo(name = "name")
    val name: String,

    /**
     * 文件路径（相对于库根目录）
     *
     * 例如: "folder/subfolder/note.md"
     */
    @ColumnInfo(name = "path")
    val path: String,

    /**
     * 最后修改时间（毫秒时间戳）
     */
    @ColumnInfo(name = "last_modified")
    val lastModified: Long,

    /**
     * 文件大小（字节）
     */
    @ColumnInfo(name = "size")
    val size: Long,

    /**
     * 所属库 ID
     */
    @ColumnInfo(name = "library_id")
    val libraryId: String,

    /**
     * 内容预览（前 200 字）
     *
     * 用于列表展示，避免每次都读取完整文件
     */
    @ColumnInfo(name = "content_preview")
    val contentPreview: String = "",

    /**
     * 完整正文内容
     *
     * 用于可用版全文搜索，避免搜索时再次扫文件系统
     */
    @ColumnInfo(name = "content_text")
    val contentText: String = "",

    /**
     * 是否已删除（软删除标记）
     *
     * 0 = 未删除，1 = 已删除
     * 未来可以用于实现回收站功能
     */
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Int = 0
)

/**
 * 将 MarkdownFileEntity 转换为 MarkdownFileMeta（列表/搜索用）
 */
fun MarkdownFileEntity.toMarkdownFileMeta(): MarkdownFileMeta {
    return MarkdownFileMeta(
        uri = uri,
        name = name,
        path = path,
        lastModified = lastModified,
        size = size,
        preview = contentPreview
    )
}

/**
 * Markdown file 摘要——不含 content_text，用于列表/搜索展示
 *
 * 避免 SELECT * 加载完整正文内容导致的 I/O 和内存开销
 */
data class MarkdownFileSummary(
    @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "content_preview") val contentPreview: String
)

fun MarkdownFileSummary.toMarkdownFileMeta(): MarkdownFileMeta {
    return MarkdownFileMeta(
        uri = uri,
        name = name,
        path = path,
        lastModified = lastModified,
        size = size,
        preview = contentPreview
    )
}
