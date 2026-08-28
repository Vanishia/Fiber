package com.bird.fiber.data.local.library

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Markdown 文件数据访问对象
 *
 * 提供数据库操作方法
 */
@Dao
interface MarkdownFileDao {

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
        ORDER BY last_modified DESC
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
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name
        FROM markdown_files
        WHERE library_id = :libraryId AND is_deleted = 0
        ORDER BY last_modified DESC
    """)
    fun getFilesByLibrarySummary(libraryId: String): PagingSource<Int, MarkdownFileSummary>

    /**
     * 搜索文件摘要（文件名 + 正文匹配，不返回完整 content_text，性能优化）
     *
     * match_snippet：正文命中时，截取命中位置附近的一段原文（前约 60 字符上下文，
     * 共 200 字符，非开头处加省略号前缀）；仅标题/路径命中时回退为 content_preview
     */
    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id,
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
        ORDER BY last_modified DESC
    """)
    fun searchFilesSummary(libraryId: String, query: String): PagingSource<Int, MarkdownFileSummary>

    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id,
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
        ORDER BY CASE
            WHEN name = :query OR name = :query || '.md' THEN 0
            WHEN name LIKE :query || '%' THEN 1
            WHEN path LIKE '%' || :query || '%' THEN 2
            ELSE 3
        END, last_modified DESC
    """)
    fun searchFilesByRelevance(libraryId: String, query: String): PagingSource<Int, MarkdownFileSummary>

    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id,
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
        ORDER BY CASE
            WHEN name = :query OR name = :query || '.md' THEN 0
            WHEN name LIKE :query || '%' THEN 1
            WHEN path LIKE '%' || :query || '%' THEN 2
            ELSE 3
        END, last_modified DESC
    """)
    fun searchAllFilesByRelevance(query: String): PagingSource<Int, MarkdownFileSummary>

    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id,
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
        ORDER BY last_modified DESC
    """)
    fun searchAllFilesByModified(query: String): PagingSource<Int, MarkdownFileSummary>

    /**
     * 获取指定库的文件数量
     */
    @Query("SELECT COUNT(*) FROM markdown_files WHERE library_id = :libraryId AND is_deleted = 0")
    suspend fun getFileCount(libraryId: String): Int

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
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id,
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
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id,
               (SELECT name FROM libraries WHERE id = markdown_files.library_id) AS library_name
        FROM markdown_files
        WHERE library_id = :libraryId AND is_deleted = 0
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomFileSummaryByLibrary(libraryId: String): MarkdownFileSummary?

    /**
     * 观察全库笔记的文件名与修改时间（记录热力图数据源）
     *
     * 返回 Flow：索引任何变化（保存/删除/同步）都会自动推送新聚合结果
     */
    @Query("""
        SELECT name, last_modified FROM markdown_files
        WHERE is_deleted = 0
    """)
    fun observeHeatmapEntries(): Flow<List<MarkdownHeatmapEntry>>

    /**
     * 获取所有库的文件摘要（"全部笔记"页面，按修改时间倒序）
     */
    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id,
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
        SELECT uri, name, path, last_modified, size, content_preview, has_image, first_image_path, library_id,
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
}
