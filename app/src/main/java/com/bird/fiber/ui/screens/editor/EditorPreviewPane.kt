package com.bird.fiber.ui.screens.editor

import android.text.Spanned
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun EditorPreviewPane(
    renderedMarkdown: Spanned?,
    isRendering: Boolean,
    topContentInset: Dp,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (isRendering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            MarkdownPreview(
                renderedMarkdown = renderedMarkdown,
                topContentInset = topContentInset,
                bottomContentInset = bottomContentInset,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun MarkdownPreview(
    renderedMarkdown: Spanned?,
    topContentInset: Dp,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val bodyStyle = MaterialTheme.typography.bodyLarge
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    val textSizePx = with(density) { bodyStyle.fontSize.toPx() }
    val topInsetPx = with(density) { topContentInset.roundToPx() }
    val bottomInsetPx = with(density) { (bottomContentInset + 12.dp).roundToPx() }

    // 缓存上次应用的值，避免不必要的 requestLayout
    var lastRenderedText by remember { mutableStateOf<Spanned?>(null) }
    var lastTextSizePx by remember { mutableStateOf(0f) }
    var lastTopInsetPx by remember { mutableStateOf(0) }
    var lastBottomInsetPx by remember { mutableStateOf(0) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val textView = TextView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                includeFontPadding = false
                setTextIsSelectable(true)
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setPadding(0, topInsetPx, 0, bottomInsetPx)
                setBackgroundColor(backgroundColor)
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            }

            val scrollView = ScrollView(ctx).apply {
                isFillViewport = true
                setBackgroundColor(backgroundColor)
                isVerticalScrollBarEnabled = false
                addView(textView)
                tag = textView
            }

            scrollView
        },
        update = { scrollView ->
            val textView = scrollView.tag as TextView
            var needsLayout = false

            // 内容变化时更新文本（引用相等性检查，Spanned 不可变所以安全）
            if (lastRenderedText !== renderedMarkdown) {
                textView.text = renderedMarkdown ?: ""
                lastRenderedText = renderedMarkdown
                needsLayout = true
            }

            if (lastTextSizePx != textSizePx) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                lastTextSizePx = textSizePx
                needsLayout = true
            }

            if (lastTopInsetPx != topInsetPx || lastBottomInsetPx != bottomInsetPx) {
                textView.setPadding(0, topInsetPx, 0, bottomInsetPx)
                lastTopInsetPx = topInsetPx
                lastBottomInsetPx = bottomInsetPx
                needsLayout = true
            }

            // 颜色/行间距变化不需要 requestLayout
            textView.setLineSpacing(0f, 1.05f)
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            textView.setBackgroundColor(backgroundColor)
            scrollView.setBackgroundColor(backgroundColor)

            if (needsLayout) {
                textView.requestLayout()
                scrollView.requestLayout()
            }
        }
    )
}
