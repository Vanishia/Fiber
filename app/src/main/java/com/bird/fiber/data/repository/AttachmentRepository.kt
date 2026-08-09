package com.bird.fiber.data.repository

import com.bird.fiber.data.model.Attachment
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.LibraryTarget

interface AttachmentRepository {
    suspend fun copyImage(sourceUri: String, target: LibraryTarget? = null): FileResult<Attachment>

    suspend fun delete(uri: String): FileResult<Unit>

    fun resolveUri(markdownFileUri: String, relativePath: String): String?
}
