package com.bird.fiber.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber

/**
 * 分享工具
 *
 * 笔记文件在 SAF 树下，URI 本身就是 content:// 文档 URI，
 * 分享时附加 FLAG_GRANT_READ_URI_PERMISSION 即可给接收方临时读权限，
 * 无需 FileProvider
 */
object ShareHelper {

    /**
     * 弹出系统分享面板分享笔记文件
     *
     * mimeType 用 text/plain 以兼容更多接收方（LocalSend、QQ 等）
     *
     * @param context 上下文
     * @param fileUri 笔记的 SAF document URI
     * @param fileName 文件名（不含后缀），用于分享面板标题
     */
    fun shareMarkdownFile(context: Context, fileUri: String, fileName: String) {
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(fileUri))
                putExtra(Intent.EXTRA_TITLE, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "分享笔记"))
        }.onFailure { e ->
            Timber.e(e, "分享失败: $fileUri")
        }
    }
}
