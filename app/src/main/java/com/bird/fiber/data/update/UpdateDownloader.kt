package com.bird.fiber.data.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.bird.fiber.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 将 APK 交给系统下载，使用应用专属目录以兼容 Android 8.1 且无需存储权限。 */
@Singleton
class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun enqueue(update: AppUpdateInfo): Long = withContext(Dispatchers.IO) {
        val downloadUrl = requireNotNull(update.downloadUrl?.takeIf { it.isNotBlank() }) {
            "此版本没有 APK 附件"
        }
        val uri = Uri.parse(downloadUrl)
        require((uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()) {
            "无效的 APK 下载地址"
        }
        val downloadManager = checkNotNull(context.getSystemService(DownloadManager::class.java)) {
            "系统下载管理器不可用"
        }
        val version = update.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        // 每次请求使用不同文件名，避免重试时已有安装包阻止下载。
        val fileName = "Fiber-$version-${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(uri)
            .setTitle("Fiber ${update.versionName}")
            .setDescription("正在下载更新安装包")
            .setMimeType("application/vnd.android.package-archive")
            .addRequestHeader("User-Agent", "Fiber-Android/${BuildConfig.VERSION_NAME}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        downloadManager.enqueue(request)
    }
}
