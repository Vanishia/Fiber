package com.bird.fiber.data.local

import android.content.ContentResolver
import android.net.Uri
import timber.log.Timber
import java.io.BufferedReader

class MarkdownContentReader {
    fun read(contentResolver: ContentResolver, uri: Uri): String {
        return try {
            val content = StringBuilder()
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(inputStream.reader()).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        content.append(line).append("\n")
                    }
                }
            }
            content.toString().trimEnd('\n')
        } catch (e: Exception) {
            Timber.e(e, "FileIndexer: read content failed uri=%s", uri)
            ""
        }
    }
}
