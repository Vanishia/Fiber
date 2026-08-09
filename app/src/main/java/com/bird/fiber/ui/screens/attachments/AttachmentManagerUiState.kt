package com.bird.fiber.ui.screens.attachments

import com.bird.fiber.data.model.ManagedAttachment

enum class AttachmentFilter(val label: String) {
    ALL("全部"),
    REFERENCED("有文件关联"),
    ORPHANED("无文件关联")
}

data class AttachmentManagerUiState(
    val libraryName: String = "",
    val attachments: List<ManagedAttachment> = emptyList(),
    val filter: AttachmentFilter = AttachmentFilter.ALL,
    val selectedUris: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isReferencesLoading: Boolean = true,
    val referencesLoaded: Boolean = false,
    val referenceError: String? = null,
    val isDeleting: Boolean = false,
    val error: String? = null,
    val message: String? = null
) {
    val filteredAttachments: List<ManagedAttachment>
        get() = if (!referencesLoaded) {
            attachments
        } else when (filter) {
            AttachmentFilter.ALL -> attachments
            AttachmentFilter.REFERENCED -> attachments.filter { it.isReferenced }
            AttachmentFilter.ORPHANED -> attachments.filterNot { it.isReferenced }
        }

    val isSelecting: Boolean get() = selectedUris.isNotEmpty()
}
