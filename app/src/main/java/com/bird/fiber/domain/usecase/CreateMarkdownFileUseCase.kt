package com.bird.fiber.domain.usecase

import com.bird.fiber.data.model.FileError
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.data.repository.FileRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 创建 Markdown 文件用例
 *
 * 职责：封装创建文件的完整业务逻辑
 * - 验证内容不为空
 * - 自动生成文件名（如果未提供）
 * - 获取当前库 URI
 * - 调用 Repository 创建文件
 *
 * 设计原则：
 * - 单一职责：负责"创建文件"这一个业务场景
 * - 依赖倒置：依赖 FileRepository 接口，不依赖具体实现
 * - 易于测试：可以 Mock FileRepository 进行测试
 */
@Singleton
class CreateMarkdownFileUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val generateFileName: GenerateFileNameUseCase
) {

    /**
     * 创建 Markdown 文件
     *
     * @param folderUri 目标文件夹 URI（如果为 null，则使用当前选中的库）
     * @param content 文件内容
     * @param fileName 文件名（可选，不提供则自动生成）
     * @return 创建结果
     */
    suspend operator fun invoke(
        folderUri: String? = null,
        content: String,
        fileName: String? = null
    ): FileResult<MarkdownFileMeta> {
        // 1. 验证内容不为空
        if (content.isBlank()) {
            return FileResult.Error(
                FileError.Unknown("内容不能为空")
            )
        }

        // 2. 确定目标文件夹 URI
        val actualFolderUri = folderUri ?: run {
            // 如果未提供 folderUri，则使用当前选中的库
            try {
                fileRepository.currentFolderUri.firstOrNull()
            } catch (e: Exception) {
                return FileResult.Error(
                    FileError.Unknown("获取当前库失败: ${e.message}")
                )
            }
        }

        // 3. 检查是否有有效的文件夹 URI
        if (actualFolderUri == null) {
            return FileResult.Error(
                FileError.Unknown("未选择笔记库，请先添加库")
            )
        }

        // 4. 生成或使用提供的文件名
        val actualFileName = fileName ?: generateFileName()

        // 5. 调用 Repository 创建文件
        return fileRepository.createMarkdownFile(
            folderUri = actualFolderUri,
            fileName = actualFileName,
            content = content
        )
    }
}
