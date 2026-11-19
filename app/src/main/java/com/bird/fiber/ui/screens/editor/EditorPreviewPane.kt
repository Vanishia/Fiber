package com.bird.fiber.ui.screens.editor

import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnPreDraw
import androidx.core.widget.TextViewCompat
import com.bird.fiber.R
import com.bird.fiber.data.local.library.NoteTagLabel
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors
import com.bird.fiber.ui.theme.LocalLineHeightScale
import kotlin.math.roundToInt

@Composable
internal fun EditorPreviewPane(
    renderedMarkdown: Spanned?,
    isRendering: Boolean,
    applyPreview: (TextView, Spanned) -> Unit,
    noteTags: List<NoteTagLabel>,
    onTagClick: (String) -> Unit,
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
                applyPreview = applyPreview,
                noteTags = noteTags,
                onTagClick = onTagClick,
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
    applyPreview: (TextView, Spanned) -> Unit,
    noteTags: List<NoteTagLabel>,
    onTagClick: (String) -> Unit,
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
    val tagContainerColor = MaterialTheme.colorScheme.secondaryContainer.toArgb()
    val tagContentColor = MaterialTheme.colorScheme.onSecondaryContainer.toArgb()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val backgroundColor = LocalFiberSurfaceColors.current.pageBackground.toArgb()
    val textSizePx = with(density) { bodyStyle.fontSize.toPx() }
    // 与编辑视图一致：精确行高 = 字号 × 共用倍数，再叠加设置里的行距缩放
    val lineHeightScale = LocalLineHeightScale.current
    val lineHeightPx = with(density) {
        (bodyStyle.fontSize * BODY_LINE_HEIGHT_MULTIPLIER * lineHeightScale).toPx().roundToInt()
    }
    val topInsetPx = with(density) { topContentInset.roundToPx() }
    val bottomInsetPx = with(density) { (bottomContentInset + 12.dp).roundToPx() }
    val tagGapPx = with(density) { 8.dp.roundToPx() }
    val tagHorizontalPaddingPx = with(density) { 12.dp.roundToPx() }
    val tagHeightPx = with(density) { 36.dp.roundToPx() }
    val tagCornerPx = with(density) { 18.dp.toPx() }
    val compactTextBottomPx = with(density) { 12.dp.roundToPx() }
    val currentOnTagClick = rememberUpdatedState(onTagClick)

    // 缓存上次应用的值，避免不必要的 requestLayout
    var lastRenderedText by remember { mutableStateOf<Spanned?>(null) }
    var lastTextSizePx by remember { mutableStateOf(0f) }
    var lastLineHeightPx by remember { mutableStateOf(0) }
    var lastTopInsetPx by remember { mutableStateOf(0) }
    var lastBottomInsetPx by remember { mutableStateOf(0) }
    var lastTags by remember { mutableStateOf<List<NoteTagLabel>>(emptyList()) }
    var lastTagStyle by remember { mutableStateOf(Triple(0, 0, 0)) }
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

            val divider = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    with(density) { 1.dp.roundToPx() }
                ).apply { topMargin = with(density) { 8.dp.roundToPx() } }
                setBackgroundColor(dividerColor)
            }
            val tagChips = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val tagScroller = HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = false
                addView(tagChips)
            }
            val tagLabel = TextView(ctx).apply {
                text = ctx.getString(R.string.editor_preview_tags)
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(tagContentColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            val tagLine = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(tagLabel, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    tagHeightPx
                ).apply { marginEnd = tagGapPx })
                addView(tagScroller, LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ))
            }
            val tagSection = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                addView(divider)
                addView(tagLine)
            }
            val document = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(textView)
                addView(tagSection)
            }

            val scrollView = ScrollView(ctx).apply {
                isFillViewport = true
                setBackgroundColor(backgroundColor)
                isVerticalScrollBarEnabled = false
                addView(document)
                tag = PreviewViews(textView, tagSection, tagLine, tagChips, tagLabel, divider)
            }

            // 上报滚动比例，供切换到编辑时恢复位置
            scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val maxScroll = (scrollView.getChildAt(0)?.height ?: 0) - scrollView.height
                if (maxScroll > 0) onScrollFractionChanged(scrollY / maxScroll.toFloat())
            }

            scrollView
        },
        onRelease = { scrollView ->
            scrollView.setOnScrollChangeListener(null)
            NativePreviewBinding.release((scrollView.tag as PreviewViews).textView)
        },
        update = { scrollView ->
            val views = scrollView.tag as PreviewViews
            val textView = views.textView
            var needsLayout = false

            // 内容变化时更新文本（引用相等性检查，Spanned 不可变所以安全）
            if (lastRenderedText !== renderedMarkdown) {
                NativePreviewBinding.apply(textView, renderedMarkdown, applyPreview)
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

            val textBottomInsetPx = if (noteTags.isEmpty()) bottomInsetPx else compactTextBottomPx
            if (lastTopInsetPx != topInsetPx || lastBottomInsetPx != textBottomInsetPx) {
                textView.setPadding(0, topInsetPx, 0, textBottomInsetPx)
                lastTopInsetPx = topInsetPx
                lastBottomInsetPx = textBottomInsetPx
                needsLayout = true
            }

            val tagStyle = Triple(tagContainerColor, tagContentColor, dividerColor)
            if (lastTags != noteTags || lastTagStyle != tagStyle) {
                views.tagSection.visibility = if (noteTags.isEmpty()) View.GONE else View.VISIBLE
                views.tagChips.removeAllViews()
                views.tagLabel.setTextColor(tagContentColor)
                views.divider.setBackgroundColor(dividerColor)
                noteTags.forEach { tag ->
                    val chip = TextView(scrollView.context).apply {
                        text = scrollView.context.getString(R.string.editor_preview_tag_chip, tag.displayPath)
                        gravity = Gravity.CENTER
                        minHeight = tagHeightPx
                        setPadding(tagHorizontalPaddingPx, 0, tagHorizontalPaddingPx, 0)
                        setTextColor(tagContentColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = tagCornerPx
                            setColor(tagContainerColor)
                        }
                        isClickable = true
                        isFocusable = true
                        contentDescription = scrollView.context.getString(
                            R.string.editor_preview_open_tag,
                            tag.displayPath
                        )
                        setOnClickListener { currentOnTagClick.value(tag.path) }
                    }
                    views.tagChips.addView(chip, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        tagHeightPx
                    ).apply { marginEnd = tagGapPx })
                }
                lastTags = noteTags
                lastTagStyle = tagStyle
                needsLayout = true
            }
            views.tagLine.setPadding(0, tagGapPx, 0, bottomInsetPx)

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

private data class PreviewViews(
    val textView: TextView,
    val tagSection: LinearLayout,
    val tagLine: LinearLayout,
    val tagChips: LinearLayout,
    val tagLabel: TextView,
    val divider: View
)
