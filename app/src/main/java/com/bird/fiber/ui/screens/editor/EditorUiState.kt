package com.bird.fiber.ui.screens.editor

import androidx.compose.ui.text.input.TextFieldValue

/**
 * 编辑器 UI 状态
 */
data class EditorUiState(
    val isLoading: Boolean = true,
    val fileName: String = "",
    val textValue: TextFieldValue = TextFieldValue(),
    val error: String? = null,
    val isAddingImage: Boolean = false,
    val isSaving: Boolean = false,
    val isPreviewMode: Boolean = true  // 预览模式（默认开启）
) {
    val content: String get() = textValue.text
}
