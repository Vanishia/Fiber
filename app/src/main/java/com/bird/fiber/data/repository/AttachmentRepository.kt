package com.bird.fiber.data.repository

import com.bird.fiber.data.model.Attachment
import com.bird.fiber.data.model.AttachmentDeletionSummary
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.LibraryTarget
import com.bird.fiber.data.model.ManagedAttachment

interface AttachmentRepository {
    suspend fun copyImage(sourceUri: String, target: LibraryTarget? = null): FileResult<Attachment>

    suspend fun delete(uri: String): FileResult<Unit>

    suspend fun listForLibrary(libraryId: String): FileResult<List<ManagedAttachment>>

    suspend fun deleteOrphans(
        libraryId: String,
        uris: Set<String>
    ): FileResult<AttachmentDeletionSummary>

    fun resolveUri(markdownFileUri: String, relativePath: String): String?
}
