package com.bird.fiber.data.local

import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownFileEntity

class MarkdownIndexWriter(
    private val markdownFileDao: MarkdownFileDao
) {
    suspend fun applySync(deletedUris: List<String>, filesToUpsert: List<MarkdownFileEntity>) {
        markdownFileDao.replaceSync(
            deletedUris = deletedUris,
            filesToUpsert = filesToUpsert
        )
    }

    suspend fun insert(entity: MarkdownFileEntity) {
        markdownFileDao.insert(entity)
    }

    suspend fun insert(
        scannedFile: ScannedFile,
        contentPreview: String,
        contentText: String
    ) {
        markdownFileDao.insert(
            scannedFile.toEntity(
                contentPreview = contentPreview,
                contentText = contentText
            )
        )
    }

    suspend fun update(entity: MarkdownFileEntity) {
        markdownFileDao.update(entity)
    }

    suspend fun delete(fileUri: String) {
        markdownFileDao.delete(fileUri)
    }
}
