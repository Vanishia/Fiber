package com.bird.fiber.data.local

import android.content.ContentResolver
import android.net.Uri
import java.io.FileNotFoundException

class MarkdownContentReader {
    fun read(contentResolver: ContentResolver, uri: Uri): String {
        val stream = contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        return stream.bufferedReader().use { it.readText() }
    }
}
