package com.bird.fiber.utils

import android.net.Uri
import android.util.Base64
import java.net.URLDecoder

/**
 * URI 工具类
 *
 * 统一处理 URI 解析、编码等逻辑，避免在多个地方重复
 */
object UriHelper {

    /**
     * 从 SAF URI 提取文件夹名称
     *
     * URI 格式示例: content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FTest
     * 解码后: /tree/primary:Documents/Test
     *
     * @param uri SAF 文件夹 URI
     * @return 文件夹名称
     */
    fun extractFolderName(uri: Uri): String {
        val path = uri.path ?: return "未命名库"
        // URI 格式: /tree/primary:Documents/...
        // 提取最后一部分
        val segments = path.split("/")
        val lastSegment = segments.lastOrNull() ?: return "未命名库"

        // 处理 "primary:Documents" 这样的格式
        return if (lastSegment.contains(":")) {
            lastSegment.split(":").lastOrNull() ?: "未命名库"
        } else {
            lastSegment
        }
    }

    /**
     * 从 URI 提取文件名
     *
     * URI 格式示例: primary:fiber测试用/第3次测试创建笔记.md
     * 解码后取最后一段，去掉库名前缀和 .md 后缀
     *
     * @param fileUri 文件 URI 字符串
     * @return 文件名（不含 .md 后缀）
     */
    fun extractFileName(fileUri: String): String {
        val decodedUri = URLDecoder.decode(fileUri, "UTF-8")
        val cleanUri = decodedUri.removePrefix("primary:")
        return cleanUri.substringAfterLast("/", "未命名")
            .removeSuffix(".md")
    }

    /**
     * Base64 编码（URL 安全）
     *
     * 用于 URI 编码以避免双重 URL 编码问题
     *
     * @param uri 要编码的 URI 字符串
     * @return Base64 编码后的字符串
     */
    fun encodeBase64(uri: String): String {
        return Base64.encodeToString(
            uri.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
    }

    /**
     * Base64 解码（URL 安全）
     *
     * @param encodedUri Base64 编码的 URI 字符串
     * @return 解码后的原始 URI
     */
    fun decodeBase64(encodedUri: String): String {
        return String(
            Base64.decode(
                encodedUri,
                Base64.URL_SAFE or Base64.NO_WRAP
            )
        )
    }
}
