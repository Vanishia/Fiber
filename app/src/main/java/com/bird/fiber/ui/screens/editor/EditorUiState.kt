package com.bird.fiber.ui.screens.editor

/**
 * 编辑器 UI 状态
 */
data class EditorUiState(
    val isLoading: Boolean = true,
    val fileName: String = "",
    val content: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
    val isPreviewMode: Boolean = true  // 预览模式（默认开启）
)
