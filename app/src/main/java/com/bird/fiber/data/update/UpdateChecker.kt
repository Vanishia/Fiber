package com.bird.fiber.data.update

import com.bird.fiber.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用更新信息（来自 GitHub Releases）
 *
 * @property versionName 去掉 "v" 前缀的版本号，如 "1.2.2"
 * @property releaseNotes release 正文（更新说明）
 * @property downloadUrl APK 附件的下载地址；release 没有 APK 附件时为 null
 * @property releasePageUrl release 网页地址，作为没有 APK 附件时的下载兜底
 */
data class AppUpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String?,
    val releasePageUrl: String
)

/**
 * 更新检查器
 *
 * 通过 GitHub Releases API 查询最新版本并与当前 [BuildConfig.VERSION_NAME] 比较
 */
@Singleton
class UpdateChecker @Inject constructor() {

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Vanishia/fiber/releases/latest"
        private const val RELEASES_PAGE_URL = "https://github.com/Vanishia/fiber/releases"
        private const val TIMEOUT_MS = 8000
    }

    /**
     * 检查是否有新版本
     *
     * 网络失败、无 release、或已是最新时返回 null
     */
    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val latest = fetchLatestRelease() ?: return@withContext null
            if (!isNewerVersion(latest.versionName, BuildConfig.VERSION_NAME)) {
                return@withContext null
            }
            latest
        } catch (e: Exception) {
            Timber.w(e, "检查更新失败")
            null
        }
    }

    private fun fetchLatestRelease(): AppUpdateInfo? {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // GitHub API 要求携带 User-Agent
            connection.setRequestProperty("User-Agent", "Fiber-Android/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept", "application/vnd.github+json")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name").trim()
            val versionName = tagName.removePrefix("v").removePrefix("V")
            if (versionName.isBlank()) return null

            // 取第一个 .apk 附件的下载地址
            var downloadUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url")
                            .takeIf { it.isNotBlank() }
                        if (downloadUrl != null) break
                    }
                }
            }

            return AppUpdateInfo(
                versionName = versionName,
                releaseNotes = json.optString("body").trim(),
                downloadUrl = downloadUrl,
                releasePageUrl = json.optString("html_url")
                    .takeIf { it.isNotBlank() }
                    ?: RELEASES_PAGE_URL
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 比较语义化版本号：remote 比 local 新时返回 true
     *
     * 按 "." 分段数值比较，无法解析的段按 0 处理
     */
    internal fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLength) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
