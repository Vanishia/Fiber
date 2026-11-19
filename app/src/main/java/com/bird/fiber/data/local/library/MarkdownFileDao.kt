package com.bird.fiber.data.local.library

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bird.fiber.domain.markdown.ParsedTag
import com.bird.fiber.domain.markdown.TagParser
import kotlinx.coroutines.flow.Flow

/**
 * Markdown 文件数据访问对象
 *
 * 提供数据库操作方法
 */
@Dao
interface MarkdownFileDao {

    data class PinnedNote(val uri: String, val name: String)

    sealed interface PinToggleResult {
        data object Pinned : PinToggleResult
        data object Unpinned : PinToggleResult
        data class ConfirmationRequired(val pinnedNote: PinnedNote) : PinToggleResult
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteTags(tags: List<NoteTagEntity>)

    @Query("DELETE FROM note_tags WHERE note_uri = :uri")
    suspend fun clearNoteTags(uri: String)

    @Query("SELECT * FROM note_tags WHERE note_uri = :uri")
    suspend fun getNoteTags(uri: String): List<NoteTagEntity>

    @Query("SELECT tag_index_version FROM markdown_files WHERE uri = :uri")
    suspend fun getTagIndexVersion(uri: String): Int?

    @Query("SELECT pinned_library_id FROM markdown_files WHERE uri = :uri")
    suspend fun getPinnedLibraryId(uri: String): String?

    @Query("SELECT COUNT(*) FROM markdown_files WHERE is_deleted = 0 AND tag_index_version < :version")
    suspend fun countPendingTags(version: Int): Int

    @Query("SELECT COUNT(*) FROM markdown_files WHERE is_deleted = 0 AND tag_index_version < :version")
    fun observePendingTags(version: Int): Flow<Int>

    @Query("SELECT uri FROM markdown_files WHERE is_deleted = 0 AND tag_index_version < :version AND uri > :afterUri ORDER BY uri LIMIT :limit")
    suspend fun pendingTagUris(version: Int, afterUri: String, limit: Int): List<String>

    @Transaction
    suspend fun writeTagIndex(file: MarkdownFileEntity, tags: List<ParsedTag>) {
        check((getTagIndexVersion(file.uri) ?: 0) <= TagParser.CURRENT_VERSION) {
            "标签索引来自较新版本，请更新应用后重试"
        }
        // REPLACE cascades old links; the complete replacement is in this same transaction.
        // Read the current database flag so a scan or stale save snapshot cannot reset a pin.
        val pinnedLibraryId = getPinnedLibraryId(file.uri)?.takeIf { it == file.libraryId }
        insert(file.copy(tagIndexVersion = TagParser.CURRENT_VERSION, pinnedLibraryId = pinnedLibraryId))
        val now = System.currentTimeMillis()
        tags.forEach { insertTag(TagEntity(it.path, it.displayPath, now)) }
        insertNoteTags(tags.map { NoteTagEntity(file.uri, it.path) })
    }

    @Transaction
    suspend fun migrateUri(oldUri: String, file: MarkdownFileEntity, links: List<NoteTagEntity>) {
        val wasPinned = getPinnedLibraryId(oldUri) == file.libraryId
        insert(file.copy(pinnedLibraryId = null))
        insertNoteTags(links.map { it.copy(noteUri = file.uri) })
        if (oldUri != file.uri) delete(oldUri)
        if (wasPinned) {
            clearPinnedNote(file.libraryId)
            setPinnedNote(file.uri, file.libraryId)
        }
    }

    @Transaction
    suspend fun replaceUriIndex(oldUri: String, file: MarkdownFileEntity, tags: List<ParsedTag>) {
        val wasPinned = getPinnedLibraryId(oldUri) == file.libraryId
        writeTagIndex(file, tags)
        if (oldUri != file.uri) delete(oldUri)
        if (wasPinned) {
            clearPinnedNote(file.libraryId)
            setPinnedNote(file.uri, file.libraryId)
        }
    }

    /**
     * 插入文件记录
     *
     * 如果 URI 已存在，则替换（用于更新文件信息）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: MarkdownFileEntity)

    /**
     * 批量插入文件记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<MarkdownFileEntity>)

    /**
     * 更新文件记录
     */
    @Update
    suspend fun update(file: MarkdownFileEntity)

    /**
     * 删除文件记录
     */
    @Query("DELETE FROM markdown_files WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Transaction
    suspend fun togglePin(uri: String, confirmedPinnedUri: String? = null): PinToggleResult {
        val file = checkNotNull(getFileByUri(uri)) { "笔记已不存在，请刷新列表后重试" }
        check(file.isDeleted == 0) { "笔记已删除，无法置顶" }
        if (file.pinnedLibraryId == file.libraryId) {
            if (confirmedPinnedUri != null) return PinToggleResult.Pinned
            clearPinnedNote(file.libraryId)
            return PinToggleResult.Unpinned
        }
        val pinnedNote = getPinnedNote(file.libraryId)
        if (pinnedNote != null && pinnedNote.uri != confirmedPinnedUri) {
            return PinToggleResult.ConfirmationRequired(pinnedNote)
        }
        clearPinnedNote(file.libraryId)
        return if (setPinnedNote(uri, file.libraryId) > 0) PinToggleResult.Pinned else PinToggleResult.Unpinned
    }

    @Query("UPDATE markdown_files SET pinned_library_id = NULL WHERE pinned_library_id = :libraryId")
    suspend fun clearPinnedNote(libraryId: String)

    @Query("UPDATE markdown_files SET pinned_library_id = :libraryId WHERE uri = :uri AND library_id = :libraryId AND is_deleted = 0")
    suspend fun setPinnedNote(uri: String, libraryId: String): Int

    @Query("SELECT uri, name FROM markdown_files WHERE library_id = :libraryId AND pinned_library_id = :libraryId AND is_deleted = 0 LIMIT 1")
    suspend fun getPinnedNote(libraryId: String): PinnedNote?

    @Query("DELETE FROM markdown_files WHERE uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    /**
     * 删除指定库的所有文件记录
     */
    @Query("DELETE FROM markdown_files WHERE library_id = :libraryId")
    suspend fun deleteByLibrary(libraryId: String)

    /**
     * 获取指定库的所有文件（按修改时间倒序）
     *
     * 返回 PagingSource，支持分页加载
     */
    @Query("""
        SELECT * FROM markdown_files
        WHERE library_id = :libraryId AND is_deleted = 0
        ORDER BY CASE WHEN pinned_library_id = library_id THEN 0 ELSE 1 END, last_modified DESC
    """)
    fun getFilesByLibrary(libraryId: String): PagingSource<Int, MarkdownFileEntity>

    /**
     * 搜索文件（文件名 + 正文）
     *
     * 返回 PagingSource，支持分页加载
     */
    @Query("""
        SELECT * FROM markdown_files
        WHERE library_id = :libraryId
        AND is_deleted = 0
        AND (
            name LIKE '%' || :query || '%'
            OR content_text LIKE '%' || :query || '%'
        )
        ORDER BY last_modified DESC
    """)
    fun searchFiles(libraryId: String, query: String): PagingSource<Int, MarkdownFileEntity>

    /**
     * 获取指定库的文件摘要（不含 content_text，性能优化）
     */
    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id, pinned_library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name
        FROM markdown_files
        WHERE library_id = :libraryId AND is_deleted = 0
        ORDER BY CASE WHEN pinned_library_id = library_id THEN 0 ELSE 1 END, last_modified DESC
    """)
    fun getFilesByLibrarySummary(libraryId: String): PagingSource<Int, MarkdownFileSummary>

    /**
     * 搜索文件摘要（文件名 + 正文匹配，不返回完整 content_text，性能优化）
     *
     * match_snippet：正文命中时，截取命中位置附近的一段原文（前约 60 字符上下文，
     * 共 200 字符，非开头处加省略号前缀）；仅标题/路径命中时回退为 content_preview
     */
    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id, pinned_library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name,
               CASE
                   WHEN instr(lower(content_text), lower(:query)) > 0 THEN
                       (CASE WHEN instr(lower(content_text), lower(:query)) > 61 THEN '…' ELSE '' END) ||
                       substr(content_text, MAX(1, instr(lower(content_text), lower(:query)) - 60), 200)
                   ELSE content_preview
               END AS match_snippet
        FROM markdown_files
        WHERE library_id = :libraryId
        AND is_deleted = 0
        AND (
            name LIKE '%' || :query || '%'
            OR path LIKE '%' || :query || '%'
            OR content_text LIKE '%' || :query || '%'
        )
        AND (:fromMillis IS NULL OR last_modified >= :fromMillis)
        AND (:toMillis IS NULL OR last_modified < :toMillis)
        ORDER BY last_modified DESC
    """)
    fun searchFilesSummary(libraryId: String, query: String, fromMillis: Long?, toMillis: Long?): PagingSource<Int, MarkdownFileSummary>

    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id, pinned_library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name,
               CASE
                   WHEN instr(lower(content_text), lower(:query)) > 0 THEN
                       (CASE WHEN instr(lower(content_text), lower(:query)) > 61 THEN '…' ELSE '' END) ||
                       substr(content_text, MAX(1, instr(lower(content_text), lower(:query)) - 60), 200)
                   ELSE content_preview
               END AS match_snippet
        FROM markdown_files
        WHERE library_id = :libraryId AND is_deleted = 0
        AND (name LIKE '%' || :query || '%' OR path LIKE '%' || :query || '%' OR content_text LIKE '%' || :query || '%')
        AND (:fromMillis IS NULL OR last_modified >= :fromMillis)
        AND (:toMillis IS NULL OR last_modified < :toMillis)
        ORDER BY CASE
            WHEN name = :query OR name = :query || '.md' THEN 0
            WHEN name LIKE :query || '%' THEN 1
            WHEN path LIKE '%' || :query || '%' THEN 2
            ELSE 3
        END, last_modified DESC
    """)
    fun searchFilesByRelevance(libraryId: String, query: String, fromMillis: Long?, toMillis: Long?): PagingSource<Int, MarkdownFileSummary>

    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id, pinned_library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name,
               CASE
                   WHEN instr(lower(content_text), lower(:query)) > 0 THEN
                       (CASE WHEN instr(lower(content_text), lower(:query)) > 61 THEN '…' ELSE '' END) ||
                       substr(content_text, MAX(1, instr(lower(content_text), lower(:query)) - 60), 200)
                   ELSE content_preview
               END AS match_snippet
        FROM markdown_files
        WHERE is_deleted = 0
        AND (name LIKE '%' || :query || '%' OR path LIKE '%' || :query || '%' OR content_text LIKE '%' || :query || '%')
        AND (:fromMillis IS NULL OR last_modified >= :fromMillis)
        AND (:toMillis IS NULL OR last_modified < :toMillis)
        ORDER BY CASE
            WHEN name = :query OR name = :query || '.md' THEN 0
            WHEN name LIKE :query || '%' THEN 1
            WHEN path LIKE '%' || :query || '%' THEN 2
            ELSE 3
        END, last_modified DESC
    """)
    fun searchAllFilesByRelevance(query: String, fromMillis: Long?, toMillis: Long?): PagingSource<Int, MarkdownFileSummary>

    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id, pinned_library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name,
               CASE
                   WHEN instr(lower(content_text), lower(:query)) > 0 THEN
                       (CASE WHEN instr(lower(content_text), lower(:query)) > 61 THEN '…' ELSE '' END) ||
                       substr(content_text, MAX(1, instr(lower(content_text), lower(:query)) - 60), 200)
                   ELSE content_preview
               END AS match_snippet
        FROM markdown_files
        WHERE is_deleted = 0
        AND (name LIKE '%' || :query || '%' OR path LIKE '%' || :query || '%' OR content_text LIKE '%' || :query || '%')
        AND (:fromMillis IS NULL OR last_modified >= :fromMillis)
        AND (:toMillis IS NULL OR last_modified < :toMillis)
        ORDER BY last_modified DESC
    """)
    fun searchAllFilesByModified(query: String, fromMillis: Long?, toMillis: Long?): PagingSource<Int, MarkdownFileSummary>

    /**
     * 获取指定库的文件数量
     */
    @Query("SELECT COUNT(*) FROM markdown_files WHERE library_id = :libraryId AND is_deleted = 0")
    suspend fun getFileCount(libraryId: String): Int

    /**
     * 统计所有库中缺少摘要（content_preview 为空）的笔记数
     *
     * 数据库迁移会清空摘要触发重建索引，启动时据此判断是否需要全库迁移
     */
    @Query("SELECT COUNT(*) FROM markdown_files WHERE content_preview = '' AND is_deleted = 0")
    suspend fun countMissingPreview(): Int

    /**
     * 根据 URI 获取文件
     */
    @Query("SELECT * FROM markdown_files WHERE uri = :uri")
    suspend fun getFileByUri(uri: String): MarkdownFileEntity?

    /**
     * 获取指定库的所有文件 URI（用于同步检查）
     */
    @Query("SELECT uri FROM markdown_files WHERE library_id = :libraryId AND is_deleted = 0")
    suspend fun getAllUrisByLibrary(libraryId: String): List<String>

    /**
     * 获取指定库所有含图片笔记的全文（用于附件引用扫描）
     *
     * 附件管理页计算"哪些笔记引用了某张图片"时改从索引库取全文，
     * 避免遍历文件系统逐篇读取；has_image = 1 预过滤掉无图笔记
     */
    @Query("""
        SELECT uri, name, content_text FROM markdown_files
        WHERE library_id = :libraryId AND is_deleted = 0
          AND has_image = 1 AND content_text != ''
    """)
    suspend fun getImageNoteContentsByLibrary(libraryId: String): List<MarkdownImageNoteContent>

    @Query("""
        SELECT uri, last_modified,
               CASE WHEN content_preview = '' THEN 0 ELSE 1 END AS has_preview,
               CASE WHEN content_text = '' THEN 0 ELSE 1 END AS has_search_content
        FROM markdown_files
        WHERE library_id = :libraryId AND is_deleted = 0
    """)
    suspend fun getIndexSnapshotsByLibrary(libraryId: String): List<MarkdownIndexSnapshot>

    @Transaction
    suspend fun replaceSync(deletedUris: List<String>, filesToUpsert: List<MarkdownFileEntity>) {
        if (deletedUris.isNotEmpty()) {
            deleteByUris(deletedUris)
        }
        if (filesToUpsert.isNotEmpty()) {
            insertAll(filesToUpsert)
        }
    }

    /**
     * 观察指定库的文件数量变化
     */
    @Query("SELECT COUNT(*) FROM markdown_files WHERE library_id = :libraryId AND is_deleted = 0")
    fun observeFileCount(libraryId: String): Flow<Int>

    /**
     * 获取指定库最近的 N 个文件（用于预加载预览）
     */
    @Query("""
        SELECT * FROM markdown_files
        WHERE library_id = :libraryId AND is_deleted = 0
        ORDER BY last_modified DESC
        LIMIT :limit
    """)
    suspend fun getRecentFiles(libraryId: String, limit: Int): List<MarkdownFileEntity>

    /**
     * 随机获取一条文件摘要（用于"随机漫步"，全库范围）
     *
     * 返回摘要而非完整实体，避免加载 content_text；
     * ORDER BY RANDOM() 在几万条记录规模下开销可接受，
     * 让沉底的旧碎片有均等机会重新浮现
     */
    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id, pinned_library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name
        FROM markdown_files
        WHERE is_deleted = 0
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomFileSummary(): MarkdownFileSummary?

    /**
     * 随机获取一条文件摘要（用于"随机漫步"，指定库范围）
     */
    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id, pinned_library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name
        FROM markdown_files
        WHERE library_id = :libraryId AND is_deleted = 0
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomFileSummaryByLibrary(libraryId: String): MarkdownFileSummary?

    /**
     * 观察全库笔记的文件名、修改时间与正文字数（记录热力图数据源）
     *
     * 返回 Flow：索引任何变化（保存/删除/同步）都会自动推送新聚合结果
     */
    @Query("""
        SELECT name, last_modified, LENGTH(content_text) AS char_count, library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name
        FROM markdown_files
        WHERE is_deleted = 0
    """)
    fun observeHeatmapEntries(): Flow<List<MarkdownHeatmapEntry>>

    /**
     * 各库笔记数统计（热力图页"各库笔记数"横条图），按数量降序
     */
    @Query("""
        SELECT (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name,
               COUNT(*) AS count
        FROM markdown_files
        WHERE is_deleted = 0
        GROUP BY library_id
        ORDER BY count DESC
    """)
    fun observeLibraryNoteCounts(): Flow<List<LibraryNoteCount>>

    /**
     * 获取所有库的文件摘要（"全部笔记"页面，按修改时间倒序）
     */
    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id, pinned_library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name
        FROM markdown_files
        WHERE is_deleted = 0
        ORDER BY last_modified DESC
    """)
    fun getAllFilesSummary(): PagingSource<Int, MarkdownFileSummary>

    /**
     * 获取"当日笔记"：热力图色块点击后的落地页（按修改时间倒序）
     *
     * 口径与热力图聚合一致：
     * - 文件名是当天时间戳的快速笔记 → 按创建日归入
     * - 其余文件名 → 按最后修改时间落入 [dayStartMillis, dayEndMillis) 归入
     *
     * 已知偏差：名字形如时间戳但日期非法（如 26-13-01）的文件，
     * SQL 无法判断日期合法性，会被两个分支同时排除
     */
    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id, pinned_library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name
        FROM markdown_files
        WHERE is_deleted = 0
        AND (
            name GLOB :quickNoteGlob
            OR (
                name NOT GLOB :anyQuickNoteGlob
                AND last_modified >= :dayStartMillis
                AND last_modified < :dayEndMillis
            )
        )
        ORDER BY last_modified DESC
    """)
    fun getFilesByDaySummary(
        quickNoteGlob: String,
        anyQuickNoteGlob: String,
        dayStartMillis: Long,
        dayEndMillis: Long
    ): PagingSource<Int, MarkdownFileSummary>

    /** 每日笔记内搜索，与 getFilesByDaySummary 使用相同的日期归档规则。 */
    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id, pinned_library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name,
               CASE
                   WHEN :query != '' AND instr(lower(content_text), lower(:query)) > 0 THEN
                       (CASE WHEN instr(lower(content_text), lower(:query)) > 61 THEN '…' ELSE '' END) ||
                       substr(content_text, MAX(1, instr(lower(content_text), lower(:query)) - 60), 200)
                   ELSE content_preview
               END AS match_snippet
        FROM markdown_files
        WHERE is_deleted = 0
        AND (:libraryId IS NULL OR library_id = :libraryId)
        AND (name LIKE '%' || :query || '%' OR path LIKE '%' || :query || '%' OR content_text LIKE '%' || :query || '%')
        AND (
            name GLOB :quickNoteGlob
            OR (name NOT GLOB :anyQuickNoteGlob AND last_modified >= :dayStartMillis AND last_modified < :dayEndMillis)
        )
        ORDER BY CASE WHEN :relevanceSort THEN CASE
            WHEN name = :query OR name = :query || '.md' THEN 0
            WHEN name LIKE :query || '%' THEN 1
            WHEN path LIKE '%' || :query || '%' THEN 2
            ELSE 3
        END ELSE 0 END, last_modified DESC
    """)
    fun searchFilesByDaySummary(
        libraryId: String?,
        query: String,
        quickNoteGlob: String,
        anyQuickNoteGlob: String,
        dayStartMillis: Long,
        dayEndMillis: Long,
        relevanceSort: Boolean
    ): PagingSource<Int, MarkdownFileSummary>
}
