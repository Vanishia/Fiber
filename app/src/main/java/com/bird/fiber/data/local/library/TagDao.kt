package com.bird.fiber.data.local.library

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    // No bodies and no per-node queries; parents and distinct unions are projected in memory.
    @Query("""
        SELECT t.path, t.display_path, t.created_at, t.is_manual, f.uri AS note_uri, f.last_modified
        FROM tags t LEFT JOIN note_tags nt ON nt.tag_path = t.path
        LEFT JOIN markdown_files f ON f.uri = nt.note_uri AND f.is_deleted = 0
        WHERE t.is_manual = 1 OR f.uri IS NOT NULL
        ORDER BY t.created_at, t.path
    """)
    fun observeReferences(): Flow<List<TagReferenceRow>>

    @Query("""
        SELECT display_path FROM tags WHERE path = :path
        OR substr(path, 1, length(:path) + 1) = :path || '/'
        ORDER BY created_at, path LIMIT 1
    """)
    fun observeFirstDisplayPath(path: String): Flow<String?>

    @Query("""
        SELECT t.path, t.display_path
        FROM note_tags nt JOIN tags t ON t.path = nt.tag_path
        WHERE nt.note_uri = :noteUri
        ORDER BY t.display_path COLLATE NOCASE, t.path
    """)
    fun observeNoteTags(noteUri: String): Flow<List<NoteTagLabel>>

    @Query("""
        SELECT f.uri, f.name, f.path, f.last_modified, f.size, f.content_preview,
               f.has_image, f.first_image_path, f.library_id, l.name AS library_name, NULL AS match_snippet,
               f.pinned_library_id
        FROM markdown_files f JOIN libraries l ON l.id = f.library_id
        WHERE f.is_deleted = 0 AND EXISTS (
            SELECT 1 FROM note_tags nt WHERE nt.note_uri = f.uri AND
            (nt.tag_path = :path OR substr(nt.tag_path, 1, length(:path) + 1) = :path || '/')
        ) ORDER BY f.last_modified DESC, f.uri ASC
    """)
    fun notes(path: String): PagingSource<Int, MarkdownFileSummary>

    @Query("""
        SELECT COUNT(*) FROM markdown_files f WHERE f.is_deleted = 0 AND EXISTS (
            SELECT 1 FROM note_tags nt WHERE nt.note_uri = f.uri AND
            (nt.tag_path = :path OR substr(nt.tag_path, 1, length(:path) + 1) = :path || '/')
        )
    """)
    fun observeCount(path: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Query("UPDATE tags SET is_manual = 1 WHERE path = :path")
    suspend fun retain(path: String)

    @Transaction
    suspend fun addManual(tag: TagEntity) {
        insert(tag)
        retain(tag.path)
    }

    @Query("""
        UPDATE tags SET is_manual = 0 WHERE path = :path AND is_manual = 1 AND NOT EXISTS (
            SELECT 1 FROM note_tags nt JOIN markdown_files f ON f.uri = nt.note_uri
            WHERE f.is_deleted = 0 AND (nt.tag_path = :path OR substr(nt.tag_path, 1, length(:path) + 1) = :path || '/')
        )
    """)
    suspend fun removeEmptyManual(path: String): Int
}
