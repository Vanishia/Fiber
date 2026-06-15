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
        SELECT uri, name, path, last_modified, size, content_preview
        FROM markdown_files
        WHERE library_id = :libraryId AND is_deleted = 0
        ORDER BY last_modified DESC
    """)
    fun getFilesByLibrarySummary(libraryId: String): PagingSource<Int, MarkdownFileSummary>

    /**
     * 搜索文件摘要（文件名 + 正文匹配，不含 content_text，性能优化）
     */
    @Query("""
        SELECT uri, name, path, last_modified, size, content_preview
        FROM markdown_files
        WHERE library_id = :libraryId
        AND is_deleted = 0
        AND (
            name LIKE '%' || :query || '%'
            OR content_text LIKE '%' || :query || '%'
        )
        ORDER BY last_modified DESC
    """)
    fun searchFilesSummary(libraryId: String, query: String): PagingSource<Int, MarkdownFileSummary>

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
     * 获取指定库的所有文件（用于同步对比）
     */
    @Query("SELECT * FROM markdown_files WHERE library_id = :libraryId AND is_deleted = 0")
    suspend fun getAllByLibrary(libraryId: String): List<MarkdownFileEntity>

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
}
