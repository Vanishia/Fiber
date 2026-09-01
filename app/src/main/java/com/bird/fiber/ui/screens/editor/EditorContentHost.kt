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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 编辑/预览之间共享的滚动比例
 *
 * 故意不用 Compose State：滚动期间每帧都在变，用普通变量承载，
 * 只在切换视图组合新面板时读取一次，避免滚动引发整树重组
 */
private class ScrollFractionHolder {
    var fraction: Float? = null
}

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
    // 编辑/预览切换时停留在相近滚动位置
    val scrollFractionHolder = remember { ScrollFractionHolder() }

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
                initialScrollFraction = scrollFractionHolder.fraction,
                onScrollFractionChanged = { scrollFractionHolder.fraction = it },
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
                initialScrollFraction = scrollFractionHolder.fraction,
                onScrollFractionChanged = { scrollFractionHolder.fraction = it },
                modifier = contentModifier
            )
        }
    }
}
