package com.bird.fiber.ui.screens.attachments

import com.bird.fiber.data.model.ManagedAttachment

enum class AttachmentFilter(val label: String) {
    ALL("全部"),
    REFERENCED("有文件关联"),
    ORPHANED("无文件关联")
}

/**
 * 链接到某张附件的笔记，用于附件管理页的关联预览
 *
 * @property preview 摘要（索引中的内容预览，未索引时取正文开头）
 * @property content 全文；读取失败时为 null，界面退回展示 [preview]
 */
data class LinkedNote(
    val fileUri: String,
    val fileName: String,
    val preview: String = "",
    val content: String? = null,
    val lastModified: Long = 0L
)

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
    val message: String? = null,
    /** 多篇笔记链接同一附件时的选择菜单内容；null 表示不展示 */
    val linkedNoteChoices: List<LinkedNote>? = null,
    /** 正在预览的关联笔记；null 表示不展示 */
    val viewingLinkedNote: LinkedNote? = null
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
