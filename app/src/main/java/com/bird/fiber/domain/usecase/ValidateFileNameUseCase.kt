package com.bird.fiber.domain.usecase

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 验证文件名用例
 *
 * 职责：验证文件名是否符合规则
 * - 文件名不能为空
 * - 文件名必须以 .md 结尾（不区分大小写）
 *
 * 设计原则：
 * - 单一职责：只负责文件名验证
 * - 无状态：不依赖外部状态
 * - 易于测试：纯函数，返回 Result 类型便于测试
 */
@Singleton
class ValidateFileNameUseCase @Inject constructor() {

    /**
     * 验证文件名
     *
     * @param fileName 待验证的文件名
     * @return 验证结果（成功返回文件名，失败返回异常）
     */
    operator fun invoke(fileName: String): Result<String> {
        // 1. 检查文件名是否为空
        if (fileName.isBlank()) {
            return Result.failure(IllegalArgumentException("文件名不能为空"))
        }

        // 2. 检查文件名是否以 .md 结尾
        if (!fileName.endsWith(".md", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("文件名必须以 .md 结尾"))
        }

        // 3. 验证通过
        return Result.success(fileName)
    }
}
