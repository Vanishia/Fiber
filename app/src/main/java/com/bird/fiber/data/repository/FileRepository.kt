package com.bird.fiber.data.repository

import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.MarkdownFileMeta
import kotlinx.coroutines.flow.Flow

/**
 * 文件仓库接口契约
 *
 * 这是UI层和业务逻辑层对数据层的抽象契约
 * Repository实现可以替换，只要遵循这个接口，UI层完全不需要改动
 */
interface FileRepository {

    /**
     * 监听当前选择的文件夹URI
     */
    val currentFolderUri: Flow<String?>

    /**
     * 选择并保存根文件夹
     * @return 文件夹URI
     */
    suspend fun selectRootFolder(): FileResult<String>

    /**
     * 读取文件内容
     * @param fileUri 文件URI
     * @return 文件内容
     */
    suspend fun readFileContent(fileUri: String): FileResult<String>

    /**
     * 创建新的markdown文件
     * @param folderUri 目标文件夹URI
     * @param fileName 文件名
     * @param content 初始内容
     * @return 创建的文件
     */
    suspend fun createMarkdownFile(
        folderUri: String,
        fileName: String,
        content: String = ""
    ): FileResult<MarkdownFileMeta>

    /**
     * 保存文件内容
     * @param fileUri 文件URI
     * @param content 新内容
     */
    suspend fun saveFileContent(fileUri: String, content: String): FileResult<Unit>

    /**
     * 删除文件
     * @param fileUri 文件URI
     */
    suspend fun deleteFile(fileUri: String): FileResult<Unit>

    /**
     * 重命名文件
     * @param fileUri 文件URI
     * @param newName 新文件名（不需要包含.md扩展名）
     */
    suspend fun renameFile(fileUri: String, newName: String): FileResult<Unit>

    /**
     * 检查是否已选择文件夹
     */
    suspend fun hasSelectedFolder(): Boolean
}
