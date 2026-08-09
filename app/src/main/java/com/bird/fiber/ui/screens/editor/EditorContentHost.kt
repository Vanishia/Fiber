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
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun EditorContentHost(
    uiState: EditorUiState,
    renderedMarkdown: Spanned?,
    isRendering: Boolean,
    onContentChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onImageSelected: (String) -> Unit,
    topContentInset: Dp,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier
) {
    val backgroundColor = LocalFiberSurfaceColors.current.pageBackground
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
                value = uiState.textValue,
                onContentChange = onContentChange,
                onImageSelected = onImageSelected,
                isAddingImage = uiState.isAddingImage,
                topContentInset = topContentInset,
                bottomContentInset = bottomContentInset,
                modifier = contentModifier
            )
        }
    }
}
