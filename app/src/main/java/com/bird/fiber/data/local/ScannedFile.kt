package com.bird.fiber.data.local

import com.bird.fiber.data.local.library.MarkdownFileEntity

data class ScannedFile(
    val uri: String,
    val name: String,
    val path: String,
    val lastModified: Long,
    val size: Long,
    val libraryId: String
) {
    fun toEntity(
        contentPreview: String = "",
        contentText: String = ""
    ): MarkdownFileEntity {
        return MarkdownFileEntity(
            uri = uri,
            name = name,
            path = path,
            lastModified = lastModified,
            size = size,
            libraryId = libraryId,
            contentPreview = contentPreview,
            contentText = contentText,
            isDeleted = 0
        )
    }
}
