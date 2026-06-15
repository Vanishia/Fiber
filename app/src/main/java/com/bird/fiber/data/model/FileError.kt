package com.bird.fiber.data.model

/**
 * 文件操作错误类型
 *
 * 定义了所有可能的文件操作错误，方便调用者针对不同错误做不同处理
 */
sealed class FileError {
    /**
     * 权限不足
     *
     * 用户没有授权访问这个文件夹或文件
     */
    data class PermissionDenied(val uri: String) : FileError() {
        override fun toString(): String {
            return "没有权限访问: $uri"
        }
    }

    /**
     * 文件或文件夹不存在
     *
     * 可能被外部删除了，或者 URI 无效
     */
    data class NotFound(val uri: String) : FileError() {
        override fun toString(): String {
            return "文件或文件夹不存在: $uri"
        }
    }

    /**
     * IO 操作失败
     *
     * 读取、写入、创建文件等操作失败
     */
    data class IOFailed(val uri: String, val cause: Throwable) : FileError() {
        override fun toString(): String {
            return "操作失败 [$uri]: ${cause.message}"
        }
    }

    /**
     * 其他未知错误
     *
     * 无法归类的错误
     */
    data class Unknown(val message: String, val cause: Throwable? = null) : FileError() {
        override fun toString(): String {
            return return if (cause != null) {
                "$message: ${cause.message}"
            } else {
                message
            }
        }
    }
}

/**
 * 将 FileError 转换为用户友好的错误消息
 *
 * 统一错误处理逻辑，避免在多个 ViewModel 中重复
 */
fun FileError.toUserMessage(): String = when (this) {
    is com.bird.fiber.data.model.FileError.PermissionDenied -> "没有权限访问"
    is com.bird.fiber.data.model.FileError.NotFound -> "文件或文件夹不存在"
    is com.bird.fiber.data.model.FileError.IOFailed -> "操作失败: ${(this as com.bird.fiber.data.model.FileError.IOFailed).cause?.message ?: "未知原因"}"
    is com.bird.fiber.data.model.FileError.Unknown -> (this as com.bird.fiber.data.model.FileError.Unknown).message
}
