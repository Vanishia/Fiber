package com.bird.fiber.ui.screens.quicknote

/**
 * 快速记录页面 UI 状态
 */
data class QuickNoteUiState(
    val content: String = "",           // 输入的笔记内容
    val isSaving: Boolean = false,      // 是否正在保存
    val error: String? = null           // 错误信息
)
