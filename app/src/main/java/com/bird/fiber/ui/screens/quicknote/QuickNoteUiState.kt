package com.bird.fiber.ui.screens.quicknote

import com.bird.fiber.data.model.Attachment

/**
 * 快速记录页面 UI 状态
 */
data class QuickNoteUiState(
    val content: String = "",           // 输入的笔记内容
    val attachments: List<Attachment> = emptyList(),
    val isAddingImage: Boolean = false,
    val isSaving: Boolean = false,      // 是否正在保存
    val error: String? = null           // 错误信息
)
