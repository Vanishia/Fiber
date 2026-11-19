package com.bird.fiber.data.local

import com.bird.fiber.data.local.library.FiberDatabase
import com.bird.fiber.data.local.library.TagEntity
import com.bird.fiber.data.local.library.TagReferenceRow
import com.bird.fiber.domain.markdown.TagParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class TagNode(
    val path: String,
    val displayPath: String,
    val noteCount: Int,
    val sortTime: Long,
    val isManual: Boolean,
    val hasDirectReference: Boolean,
    val children: List<TagNode>
) {
    val isExplicit get() = isManual || hasDirectReference
    val isVirtual get() = !isExplicit
    val canDelete get() = isManual && noteCount == 0
}

@Singleton
class TagRepository @Inject constructor(database: FiberDatabase) {
    private val dao = database.tagDao()
    val tree = dao.observeReferences().map(::buildTagTree).flowOn(Dispatchers.Default)
    fun notes(path: String) = dao.notes(path)
    fun count(path: String) = dao.observeCount(path)
    fun noteTags(noteUri: String) = dao.observeNoteTags(noteUri)

    fun displayPath(path: String) = dao.observeFirstDisplayPath(path).map { display ->
        // Keep the first spelling even after its last note is removed from this page.
        display?.split('/')?.take(path.count { it == '/' } + 1)?.joinToString("/") ?: path
    }

    suspend fun add(input: String): String {
        val tag = requireNotNull(TagParser.manual(input)) {
            "请输入有效标签：各级用 / 分隔，允许文字、数字、_ 和 -，最多 ${TagParser.MAX_DEPTH} 级、${TagParser.MAX_LENGTH} 字符"
        }
        fun find(nodes: List<TagNode>): TagNode? {
            nodes.forEach { node ->
                if (node.path == tag.path) return node
                find(node.children)?.let { return it }
            }
            return null
        }
        val display = find(tree.first())?.displayPath ?: tag.displayPath
        dao.addManual(TagEntity(tag.path, display, System.currentTimeMillis(), true))
        return tag.path
    }

    suspend fun delete(path: String) {
        check(dao.removeEmptyManual(path) > 0) { "只能删除没有笔记使用的手动标签" }
    }
}

/** Only compact reference metadata is loaded. Each ancestor uses a note URI set for unions. */
internal fun buildTagTree(rows: List<TagReferenceRow>): List<TagNode> {
    class Builder(val path: String, val display: String) {
        val notes = mutableSetOf<String>()
        var modified = 0L
        var created = 0L
        var manual = false
        var direct = false
    }
    val builders = linkedMapOf<String, Builder>()
    rows.forEach { row ->
        val keys = row.path.split('/')
        val display = row.displayPath.split('/')
        keys.indices.forEach { index ->
            val key = keys.take(index + 1).joinToString("/")
            val node = builders.getOrPut(key) { Builder(key, display.take(index + 1).joinToString("/")) }
            if (row.noteUri != null) {
                node.notes += row.noteUri
                node.modified = maxOf(node.modified, row.lastModified ?: 0)
            }
            // A branch of empty manual children still needs an ordering fallback.
            node.created = maxOf(node.created, row.createdAt)
            if (key == row.path) {
                node.manual = row.isManual
                node.direct = node.direct || row.noteUri != null
            }
        }
    }
    val byParent = builders.values.groupBy { it.path.substringBeforeLast('/', "") }
    fun children(parent: String): List<TagNode> = byParent[parent].orEmpty().map { node ->
        TagNode(node.path, node.display, node.notes.size,
            if (node.notes.isNotEmpty()) node.modified else node.created,
            node.manual, node.direct, children(node.path))
    }.sortedWith(compareByDescending<TagNode> { it.sortTime }.thenBy { it.path })
    return children("")
}
