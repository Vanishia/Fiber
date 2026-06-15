package com.bird.fiber.data.local

import android.content.ContentResolver
import android.net.Uri
import com.bird.fiber.utils.PreviewHelper
import timber.log.Timber

class MarkdownPreviewReader {
    fun read(contentResolver: ContentResolver, uri: Uri): String {
        return try {
            PreviewHelper.readFilePreview(
                contentResolver = contentResolver,
                uri = uri
            )
        } catch (e: Exception) {
            Timber.e(e, "FileIndexer: read preview failed uri=%s", uri)
            ""
        }
    }

    fun readFromContent(content: String): String {
        return PreviewHelper.generatePreview(content)
    }
}
