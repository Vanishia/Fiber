package com.bird.fiber.ui.screens.editor

import android.text.Spanned

data class EditorRenderState(
    val renderedMarkdown: Spanned? = null,
    val isRendering: Boolean = false
)
