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

    suspend fun getLibraryByFolderUri(folderUri: String): LibraryEntity? {
        return libraryDao.getLibraryByFolderUri(folderUri)
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
        val shouldReleasePermission = try {
            libraryDao.countOtherLibrariesByFolderUri(library.folderUri, library.id) == 0
        } catch (e: Exception) {
            Timber.e(e, "检查重复库绑定失败，保留 URI 权限")
            false
        }

        try {
            if (shouldReleasePermission) {
                val uri = Uri.parse(library.folderUri)
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                contentResolver.persistedUriPermissions.forEach { permission ->
                    if (permission.uri == uri) {
                        contentResolver.releasePersistableUriPermission(uri, flags)
                        Timber.d("已释放 URI 权限: ${library.name}")
                    }
                }
            } else {
                Timber.w("检测到相同 URI 的其他库记录，保留 URI 权限: ${library.name}")
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
     * 验证库访问权限，保留暂时不可用的库及其可用索引。
     *
     * 重新选择同一目录可恢复授权；只有用户明确移除库才级联清理笔记与标签。
     * 保留旧方法名和返回约定供现有启动协调层使用，此处不再自动删除记录。
     */
    suspend fun validateAndCleanupInvalidLibraries(contentResolver: ContentResolver): Int {
        val libraries = libraryDao.getAllLibrariesList()
        for (library in libraries) {
            if (!isUriValid(contentResolver, library.folderUri)) {
                Timber.w("笔记库授权暂不可用，保留索引，可重新选择目录恢复: ${library.name}")
            }
        }

        return 0
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
