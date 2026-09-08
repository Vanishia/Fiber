package com.bird.fiber.ui.screens.editor

import android.text.Spanned
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.TextViewCompat
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
import androidx.core.view.doOnPreDraw
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors
import com.bird.fiber.ui.theme.LocalLineHeightScale
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.image.AsyncDrawableScheduler
import kotlin.math.roundToInt

@Composable
internal fun EditorPreviewPane(
    renderedMarkdown: Spanned?,
    isRendering: Boolean,
    topContentInset: Dp,
    bottomContentInset: Dp,
    initialScrollFraction: Float? = null,
    onScrollFractionChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 只有首次渲染（还没有任何内容）才整屏 loading；
        // 后续重渲染保留旧内容，避免图片等资源未就绪时整屏空白闪烁
        if (isRendering && renderedMarkdown == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            MarkdownPreview(
                renderedMarkdown = renderedMarkdown,
                topContentInset = topContentInset,
                bottomContentInset = bottomContentInset,
                initialScrollFraction = initialScrollFraction,
                onScrollFractionChanged = onScrollFractionChanged,
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
    initialScrollFraction: Float?,
    onScrollFractionChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val bodyStyle = MaterialTheme.typography.bodyLarge
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val backgroundColor = LocalFiberSurfaceColors.current.pageBackground.toArgb()
    val textSizePx = with(density) { bodyStyle.fontSize.toPx() }
    // 与编辑视图一致：精确行高 = 字号 × 共用倍数，再叠加设置里的行距缩放
    val lineHeightScale = LocalLineHeightScale.current
    val lineHeightPx = with(density) {
        (bodyStyle.fontSize * BODY_LINE_HEIGHT_MULTIPLIER * lineHeightScale).toPx().roundToInt()
    }
    val topInsetPx = with(density) { topContentInset.roundToPx() }
    val bottomInsetPx = with(density) { (bottomContentInset + 12.dp).roundToPx() }

    // 缓存上次应用的值，避免不必要的 requestLayout
    var lastRenderedText by remember { mutableStateOf<Spanned?>(null) }
    var lastTextSizePx by remember { mutableStateOf(0f) }
    var lastLineHeightPx by remember { mutableStateOf(0) }
    var lastTopInsetPx by remember { mutableStateOf(0) }
    var lastBottomInsetPx by remember { mutableStateOf(0) }
    // 从编辑切换回来时按滚动比例恢复位置（仅首次设置文本时执行一次）
    var initialFractionApplied by remember { mutableStateOf(false) }

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

            // 上报滚动比例，供切换到编辑时恢复位置
            scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val maxScroll = (scrollView.getChildAt(0)?.height ?: 0) - scrollView.height
                if (maxScroll > 0) onScrollFractionChanged(scrollY / maxScroll.toFloat())
            }

            scrollView
        },
        update = { scrollView ->
            val textView = scrollView.tag as TextView
            var needsLayout = false

            // 内容变化时更新文本（引用相等性检查，Spanned 不可变所以安全）
            if (lastRenderedText !== renderedMarkdown) {
                AsyncDrawableScheduler.unschedule(textView)
                TableRowsFix.unschedule(textView)
                textView.text = renderedMarkdown ?: ""
                AsyncDrawableScheduler.schedule(textView)
                TableRowsFix.schedule(textView)
                lastRenderedText = renderedMarkdown
                needsLayout = true

                if (!initialFractionApplied) {
                    initialFractionApplied = true
                    val fraction = initialScrollFraction
                    if (fraction != null && fraction > 0f) {
                        // 等布局量出内容高度后再按比例滚动
                        scrollView.doOnPreDraw {
                            val maxScroll = (scrollView.getChildAt(0)?.height ?: 0) - scrollView.height
                            if (maxScroll > 0) {
                                scrollView.scrollTo(0, (fraction * maxScroll).toInt())
                            }
                        }
                    }
                }
            }

            if (lastTextSizePx != textSizePx) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                lastTextSizePx = textSizePx
                needsLayout = true
            }

            if (lastLineHeightPx != lineHeightPx) {
                TextViewCompat.setLineHeight(textView, lineHeightPx)
                lastLineHeightPx = lineHeightPx
                needsLayout = true
            }

            if (lastTopInsetPx != topInsetPx || lastBottomInsetPx != bottomInsetPx) {
                textView.setPadding(0, topInsetPx, 0, bottomInsetPx)
                lastTopInsetPx = topInsetPx
                lastBottomInsetPx = bottomInsetPx
                needsLayout = true
            }

            // 颜色变化不需要 requestLayout
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

/**
 * markwon 表格行高修正机制的本地副本（对应 TableRowsScheduler，包私有无法直接用）
 *
 * 表格行由 TableRowSpan（ReplacementSpan）绘制，首次布局时单元格 Layout 尚未构建，
 * getSize 只能报默认行高；首次绘制算出真实行高后需要重新 setText 触发布局修正。
 * markwon 依赖 setMarkdown 流程的 beforeSetText/afterSetText 挂上这套机制，
 * 而这里是 render() + 手动 setText，钩子不会执行，表格会一直保持错误行高
 * （文字溢出格子、与下文堆叠），因此手动补齐。
 */
private object TableRowsFix {

    fun schedule(textView: TextView) {
        val spans = extract(textView) ?: return
        val invalidator = object : TableRowSpan.Invalidator {
            // 与 TableRowsScheduler 一致：合并一帧内的多次 invalidate，post 一次重排
            val relayout = Runnable {
                if (textView.isAttachedToWindow) {
                    textView.text = textView.text
                }
            }

            override fun invalidate() {
                textView.removeCallbacks(relayout)
                textView.post(relayout)
            }
        }
        spans.forEach { it.invalidator(invalidator) }
    }

    fun unschedule(textView: TextView) {
        extract(textView)?.forEach { it.invalidator(null) }
    }

    private fun extract(textView: TextView): Array<out TableRowSpan>? {
        val text = textView.text
        if (text.isNullOrEmpty() || text !is Spanned) return null
        return text.getSpans(0, text.length, TableRowSpan::class.java)
            .takeIf { it.isNotEmpty() }
    }
}
