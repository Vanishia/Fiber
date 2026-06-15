package com.bird.fiber.data.model

/**
 * 文件操作结果封装
 *
 * 使用示例：
 * ```kotlin
 * when (val result = repository.getMarkdownFiles(folderUri)) {
 *     is FileResult.Success -> {
 *         val files = result.data
 *         // 处理成功情况
 *     }
 *     is FileResult.Error -> {
 *         when (result.error) {
 *             is FileError.PermissionDenied -> {
 *                 // 处理权限错误
 *             }
 *             is FileError.NotFound -> {
 *                 // 处理文件不存在
 *             }
 *             is FileError.IOFailed -> {
 *                 // 处理 IO 错误
 *             }
 *             is FileError.Unknown -> {
 *                 // 处理未知错误
 *             }
 *         }
 *     }
 *     is FileResult.Loading -> {
 *         // 显示加载状态
 *     }
 * }
 * ```
 */
sealed class FileResult<out T> {
    data class Success<T>(val data: T) : FileResult<T>()
    data class Error(val error: FileError) : FileResult<Nothing>()
    data object Loading : FileResult<Nothing>()
}
