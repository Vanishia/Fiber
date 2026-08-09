package com.bird.fiber.data.model

data class ManagedAttachment(
    val displayName: String,
    val relativePath: String,
    val uri: String,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long,
    val width: Int,
    val height: Int,
    val referencedBy: List<AttachmentReference>
) {
    val isReferenced: Boolean get() = referencedBy.isNotEmpty()

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) {
            (width.toFloat() / height).coerceIn(0.65f, 1.6f)
        } else {
            1f
        }
}

data class AttachmentReference(
    val fileUri: String,
    val fileName: String
)

data class AttachmentDeletionSummary(
    val deletedCount: Int,
    val skippedReferencedCount: Int,
    val failedCount: Int
)
