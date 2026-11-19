package com.bird.fiber.data.local

import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownFileEntity
import com.bird.fiber.data.local.library.NoteTagEntity
import com.bird.fiber.domain.markdown.TagParser

class MarkdownIndexWriter(
    private val markdownFileDao: MarkdownFileDao
) {
    suspend fun upsertBatch(filesToUpsert: List<MarkdownFileEntity>) {
        if (filesToUpsert.isNotEmpty()) {
            filesToUpsert.forEach { insert(it) }
        }
    }

    suspend fun deleteMissing(deletedUris: List<String>) {
        if (deletedUris.isNotEmpty()) {
            markdownFileDao.deleteByUris(deletedUris)
        }
    }

    suspend fun insert(entity: MarkdownFileEntity) {
        check(entity.tagIndexVersion <= TagParser.CURRENT_VERSION) {
            "标签索引来自较新版本，请更新应用后重试"
        }
        markdownFileDao.writeTagIndex(entity, TagParser.parse(entity.contentText))
    }

    suspend fun insert(
        scannedFile: ScannedFile,
        contentPreview: String,
        contentText: String,
        hasImage: Boolean,
        firstImagePath: String = ""
    ) {
        insert(
            scannedFile.toEntity(
                contentPreview = contentPreview,
                contentText = contentText,
                hasImage = hasImage,
                firstImagePath = firstImagePath
            )
        )
    }

    suspend fun update(entity: MarkdownFileEntity) {
        insert(entity)
    }

    suspend fun migrateUri(oldUri: String, entity: MarkdownFileEntity, links: List<NoteTagEntity>) {
        markdownFileDao.migrateUri(oldUri, entity, links)
    }

    suspend fun replaceUriWithContent(oldUri: String, entity: MarkdownFileEntity) {
        markdownFileDao.replaceUriIndex(oldUri, entity, TagParser.parse(entity.contentText))
    }

    suspend fun delete(fileUri: String) {
        markdownFileDao.delete(fileUri)
    }
}
