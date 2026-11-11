package com.bird.fiber.data.update

import android.app.DownloadManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDownloaderInstrumentedTest {
    @Test
    fun requestIsRegisteredWithSystemDownloadManagerAsApk() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val update = AppUpdateInfo(
            versionName = "9.9.9-test",
            releaseNotes = "测试",
            // 只验证系统交接；使用保留域名避免下载真实安装包。
            downloadUrl = "https://example.invalid/Fiber.apk",
            releasePageUrl = "https://example.invalid/releases"
        )
        val id = UpdateDownloader(context).enqueue(update)
        try {
            assertTrue(id > 0)
            downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    update.downloadUrl,
                    cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
                )
                assertEquals(
                    "application/vnd.android.package-archive",
                    cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))
                )
                assertEquals(
                    "Fiber ${update.versionName}",
                    cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                )
            }
        } finally {
            downloadManager.remove(id)
        }
    }
}
