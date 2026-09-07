package com.bird.fiber.ui.screens.filelist.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import coil.load
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.utils.FileUtils
import com.bird.fiber.utils.UriHelper
import com.bird.fiber.utils.isQuickNoteFileName
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors

/** 瀑布流首图宽高比（宽:高 = 2:3），类似社交媒体头图 */
private const val COVER_ASPECT_RATIO = 2f / 3f

/** 首图解码尺寸（px），两列布局下宽度约半屏，512 已足够清晰 */
private const val COVER_PIXELS = 512

/** 有首图的卡片内容截断行数 */
private const val CONTENT_MAX_LINES_WITH_COVER = 3

/**
 * 瀑布流模式的笔记卡片（仿小红书/Google Keep 双列方框样式）
 *
 * 有首图时：顶部 2:3 首图（上左右贴边），下面标题 + 3 行截断内容；
 * 无首图时：标题 + [contentMaxLinesNoCover] 行截断内容（调用方按位置交替 5/6 行，
 * 让两列高度错开形成瀑布流感）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WaterfallNoteCard(
    file: MarkdownFileMeta,
    modifier: Modifier = Modifier,
    displayPreview: String = file.preview,
    contentMaxLinesNoCover: Int = 5,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isDarkTheme = LocalFiberSurfaceColors.current.pageBackground.luminance() < 0.5f
    val showCover = file.hasImage && file.firstImagePath.isNotBlank()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = LocalFiberSurfaceColors.current.contentCard
        ),
        border = if (isDarkTheme) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDarkTheme) 0.dp else 2.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showCover) {
                WaterfallCoverImage(
                    noteUri = file.uri,
                    imagePath = file.firstImagePath,
                    contentDescription = file.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(COVER_ASPECT_RATIO)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
            ) {
                val fileNameWithoutExt = file.name.removeSuffix(".md")
                val isQuickNote = isQuickNoteFileName(fileNameWithoutExt)

                if (!isQuickNote) {
                    Text(
                        text = fileNameWithoutExt,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (displayPreview.isNotEmpty()) {
                    if (!isQuickNote) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = displayPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (showCover) {
                            CONTENT_MAX_LINES_WITH_COVER
                        } else {
                            contentMaxLinesNoCover
                        },
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = FileUtils.formatDate(file.lastModified),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 瀑布流卡片顶部首图（2:3，CENTER_CROP 贴满卡片上左右边缘）
 *
 * 加载与占位策略同列表缩略图（见 FileListItem 的 NoteImageThumbnail）
 */
@Composable
private fun WaterfallCoverImage(
    noteUri: String,
    imagePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val imageUri = remember(noteUri, imagePath) {
        UriHelper.resolveNoteImageUri(noteUri, imagePath)
    } ?: return

    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(placeholderColor)
                this.contentDescription = contentDescription
            }
        },
        update = { imageView ->
            imageView.load(android.net.Uri.parse(imageUri)) {
                size(COVER_PIXELS)
                crossfade(true)
            }
        },
        modifier = modifier.clipToBounds()
    )
}
