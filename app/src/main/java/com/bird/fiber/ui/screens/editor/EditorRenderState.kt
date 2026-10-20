package com.bird.fiber.ui.screens.editor

import android.text.Spanned
import com.bird.fiber.domain.markdown.DocumentSnapshot

data class EditorRenderState(
    val renderedMarkdown: Spanned? = null,
    val isRendering: Boolean = false,
    val document: DocumentSnapshot? = null,
    val error: String? = null
)
