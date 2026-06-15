package com.bird.fiber.ui.screens.editor

import android.text.Spanned
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun EditorContentHost(
    uiState: EditorUiState,
    renderedMarkdown: Spanned?,
    isRendering: Boolean,
    onContentChange: (String) -> Unit,
    topContentInset: Dp,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val contentModifier = modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)

    when {
        uiState.isLoading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(backgroundColor) },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.error != null -> {
            Box(
                modifier = modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.error ?: "未知错误",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        uiState.isPreviewMode -> {
            EditorPreviewPane(
                renderedMarkdown = renderedMarkdown,
                isRendering = isRendering,
                topContentInset = topContentInset,
                bottomContentInset = bottomContentInset,
                modifier = contentModifier
            )
        }

        else -> {
            EditorEditPane(
                content = uiState.content,
                onContentChange = onContentChange,
                topContentInset = topContentInset,
                bottomContentInset = bottomContentInset,
                modifier = contentModifier
            )
        }
    }
}
