package com.bird.fiber.data.repository

import com.bird.fiber.data.model.Attachment
import com.bird.fiber.data.model.FileResult

interface AttachmentRepository {
    suspend fun copyImage(sourceUri: String, libraryFolderUri: String? = null): FileResult<Attachment>

    fun resolveUri(markdownFileUri: String, relativePath: String): String?
}
