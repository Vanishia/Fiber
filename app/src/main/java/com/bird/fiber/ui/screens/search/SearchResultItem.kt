package com.bird.fiber.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.utils.FileUtils
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors

@Composable
internal fun SearchResultItem(
    file: MarkdownFileMeta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = ""
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = LocalFiberSurfaceColors.current.contentCard
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 13.dp, end = 13.dp, top = 11.dp, bottom = 10.dp)
        ) {
            Text(
                text = file.name.removeSuffix(".md"),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary
            )

            if (file.libraryName.isNotBlank()) {
                Text(
                    text = file.libraryName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 优先展示命中位置附近的片段，让用户看得出为什么搜到这条结果
            val snippet = file.matchSnippet?.takeIf { it.isNotBlank() } ?: file.preview
            if (snippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = highlightKeyword(
                        text = snippet,
                        keyword = searchQuery,
                        highlightStyle = keywordHighlightStyle()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FileUtils.formatDate(file.lastModified),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (file.path.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = file.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (file.hasImage) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "包含图片",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 命中词高亮样式：底色沿用主题色相，只按日夜间调整明度（不动饱和度），
 * 避免 primaryContainer 直接当底色过于刺眼；文字用跟随日夜的黑白，保留加粗
 */
@Composable
private fun keywordHighlightStyle(): SpanStyle {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val background = MaterialTheme.colorScheme.primary.withLightness(
        if (isDark) DARK_HIGHLIGHT_LIGHTNESS else LIGHT_HIGHLIGHT_LIGHTNESS
    )
    return SpanStyle(
        color = if (isDark) Color.White else Color.Black,
        background = background,
        fontWeight = FontWeight.Bold
    )
}

/** 保持色相与饱和度，仅替换 HSL 明度 */
private fun Color.withLightness(lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

private const val LIGHT_HIGHLIGHT_LIGHTNESS = 0.86f
private const val DARK_HIGHLIGHT_LIGHTNESS = 0.30f

/**
 * 将文本中所有（忽略大小写）出现的 [keyword] 应用 [highlightStyle] 高亮
 */
private fun highlightKeyword(
    text: String,
    keyword: String,
    highlightStyle: SpanStyle
): AnnotatedString {
    if (keyword.isBlank()) return AnnotatedString(text)
    val builder = AnnotatedString.Builder(text)
    var index = text.indexOf(keyword, ignoreCase = true)
    while (index >= 0) {
        builder.addStyle(highlightStyle, index, index + keyword.length)
        index = text.indexOf(keyword, startIndex = index + keyword.length, ignoreCase = true)
    }
    return builder.toAnnotatedString()
}
