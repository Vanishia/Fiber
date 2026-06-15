package com.bird.fiber.data.local.library

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 笔记库数据访问对象
 *
 * 提供对库的 CRUD 操作
 * 使用 Flow 实现响应式数据更新
 */
@Dao
interface LibraryDao {

    /**
     * 获取所有库，按最后打开时间排序
     */
    @Query("SELECT * FROM libraries ORDER BY lastOpenedAt DESC")
    fun getAllLibraries(): Flow<List<LibraryEntity>>

    /**
     * 获取当前激活的库
     */
    @Query("SELECT * FROM libraries WHERE isActive = 1 LIMIT 1")
    fun getActiveLibrary(): Flow<LibraryEntity?>

    /**
     * 根据 ID 获取库
     */
    @Query("SELECT * FROM libraries WHERE id = :libraryId")
    suspend fun getLibraryById(libraryId: String): LibraryEntity?

    /**
     * 插入新库
     * @return 新插入行的行 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrary(library: LibraryEntity): Long

    /**
     * 更新库信息
     */
    @Update
    suspend fun updateLibrary(library: LibraryEntity)

    /**
     * 删除库
     */
    @Delete
    suspend fun deleteLibrary(library: LibraryEntity)

    /**
     * 激活指定库，并取消其他库的激活状态
     */
    @Query("UPDATE libraries SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE libraries SET isActive = 1, lastOpenedAt = :timestamp WHERE id = :libraryId")
    suspend fun activateLibrary(libraryId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * 删除库（根据 ID）
     */
    @Query("DELETE FROM libraries WHERE id = :libraryId")
    suspend fun deleteLibraryById(libraryId: String)

    /**
     * 获取库的数量
     */
    @Query("SELECT COUNT(*) FROM libraries")
    fun getLibraryCount(): Flow<Int>

    /**
     * 获取所有库（非 Flow，用于验证）
     */
    @Query("SELECT * FROM libraries ORDER BY lastOpenedAt DESC")
    suspend fun getAllLibrariesList(): List<LibraryEntity>

    /**
     * 删除所有库
     */
    @Query("DELETE FROM libraries")
    suspend fun deleteAllLibraries()
}
