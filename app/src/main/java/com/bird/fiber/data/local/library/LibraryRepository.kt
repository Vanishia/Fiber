package com.bird.fiber.data.local.library

import android.content.ContentResolver
import android.net.Uri
import timber.log.Timber
import kotlinx.coroutines.flow.Flow

/**
 * 笔记库仓库
 *
 * 封装对 LibraryDao 的操作
 * 提供给 ViewModel 使用
 */
class LibraryRepository(
    private val libraryDao: LibraryDao
) {

    /**
     * 获取所有库
     */
    fun getAllLibraries(): Flow<List<LibraryEntity>> {
        return libraryDao.getAllLibraries()
    }

    /**
     * 获取当前激活的库
     */
    fun getActiveLibrary(): Flow<LibraryEntity?> {
        return libraryDao.getActiveLibrary()
    }

    /**
     * 根据 ID 获取库
     */
    suspend fun getLibraryById(libraryId: String): LibraryEntity? {
        return libraryDao.getLibraryById(libraryId)
    }

    /**
     * 添加新库
     */
    suspend fun addLibrary(library: LibraryEntity) {
        libraryDao.insertLibrary(library)
    }

    /**
     * 更新库信息
     */
    suspend fun updateLibrary(library: LibraryEntity) {
        libraryDao.updateLibrary(library)
    }

    /**
     * 删除库并释放对应的 URI 权限
     */
    suspend fun deleteLibrary(library: LibraryEntity, contentResolver: ContentResolver) {
        try {
            // 释放持久化的 URI 权限
            val uri = Uri.parse(library.folderUri)
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                       android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            // 检查是否有这个权限，有的话释放它
            contentResolver.persistedUriPermissions.forEach { permission ->
                if (permission.uri == uri) {
                    contentResolver.releasePersistableUriPermission(uri, flags)
                    Timber.d("已释放 URI 权限: ${library.name}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "释放 URI 权限失败")
        } finally {
            // 无论权限释放是否成功，都从数据库删除库记录
            libraryDao.deleteLibrary(library)
            Timber.d("已删除库记录: ${library.name}")
        }
    }

    /**
     * 删除库（根据 ID）
     */
    suspend fun deleteLibraryById(libraryId: String) {
        libraryDao.deleteLibraryById(libraryId)
    }

    /**
     * 切换到指定库
     */
    suspend fun switchLibrary(libraryId: String) {
        // 取消所有库的激活状态
        libraryDao.deactivateAll()
        // 激活指定库
        libraryDao.activateLibrary(libraryId)
    }

    /**
     * 获取库的数量
     */
    fun getLibraryCount(): Flow<Int> {
        return libraryDao.getLibraryCount()
    }

    /**
     * 验证并清理无效的库
     *
     * 检查每个库的 URI 是否仍然可访问，删除无效的库
     * 返回被删除的库数量
     */
    suspend fun validateAndCleanupInvalidLibraries(contentResolver: ContentResolver): Int {
        val libraries = libraryDao.getAllLibrariesList()
        var removedCount = 0

        for (library in libraries) {
            if (!isUriValid(contentResolver, library.folderUri)) {
                Timber.d("删除无效库: ${library.name} (${library.folderUri})")
                libraryDao.deleteLibrary(library)
                removedCount++
            }
        }

        return removedCount
    }

    /**
     * 检查 URI 是否仍然有效
     */
    private fun isUriValid(contentResolver: ContentResolver, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            // 尝试获取元数据来验证 URI 是否仍然有效
            contentResolver.persistedUriPermissions.any {
                it.uri.toString() == uriString
            }
        } catch (e: Exception) {
            Timber.e(e, "URI 验证失败: $uriString")
            false
        }
    }

    /**
     * 删除所有库（用于调试/重置）
     */
    suspend fun clearAllLibraries() {
        libraryDao.deleteAllLibraries()
        Timber.d("已删除所有库")
    }

    /**
     * 获取所有库列表（非 Flow）
     */
    suspend fun getAllLibrariesList(): List<LibraryEntity> {
        return libraryDao.getAllLibrariesList()
    }
}
