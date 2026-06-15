package com.bird.fiber.utils

import android.content.ContentResolver
import android.net.Uri
import com.bird.fiber.data.config.PreviewConfig
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 预览工具类
 *
 * 统一的预览读取逻辑，避免重复代码
 *
 * 职责：
 * - 从文件 URI 读取前 N 字符作为预览
 * - 从完整内容生成预览
 * - 统一的错误处理
 *
 * 策略说明：
 * PreviewHelper 只负责"取前 N 字符"，不添加任何截断标记。
 * 因为 UI 层已经由 Text composable 的 maxLines + TextOverflow.Ellipsis 处理了视觉截断。
 * 如果 PreviewHelper 再加 "…"，会和 UI 的 native ellipsis 重复，看起来奇怪。
 *
 * MAX_CHARS = 500 确保预览区始终有充足的内容填满 3 视觉行，
 * 无论内容是单段文字还是多段结构。
 */
object PreviewHelper {

    /**
     * 读取文件预览
     *
     * @param contentResolver ContentResolver
     * @param uri 文件 URI
     * @param maxChars 最大字符数（默认使用配置值）
     * @return 预览字符串，失败返回空字符串
     */
    fun readFilePreview(
        contentResolver: ContentResolver,
        uri: Uri,
        maxChars: Int = PreviewConfig.MAX_CHARS
    ): String {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val buffer = CharArray(maxChars + 1)
                    val charsRead = reader.read(buffer, 0, maxChars + 1)
                    if (charsRead <= 0) return ""

                    String(buffer, 0, minOf(charsRead, maxChars)).trimStart()
                }
            } ?: ""
        } catch (e: Exception) {
            Timber.e(e, "PreviewHelper: 读取文件预览失败 uri=$uri")
            ""
        }
    }

    /**
     * 从完整内容生成预览
     *
     * 用于已经读取了完整文件内容的场景。
     * 仅截取前 maxChars 个字符，不添加额外标记。
     * 视觉截断由 UI 层的 Text(maxLines=3, overflow=Ellipsis) 处理。
     *
     * @param content 完整文件内容
     * @param maxChars 最大字符数（默认使用配置值）
     * @return 预览字符串
     */
    fun generatePreview(
        content: String,
        maxChars: Int = PreviewConfig.MAX_CHARS
    ): String {
        val text = content.trimStart()
        if (text.isEmpty()) return ""
        return text.take(maxChars).trimEnd()
    }
}
