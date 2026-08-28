package com.bird.fiber.ui.screens.filelist.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import coil.load
import com.bird.fiber.data.config.PreviewConfig
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.utils.FileUtils
import com.bird.fiber.utils.UriHelper
import com.bird.fiber.utils.isQuickNoteFileName
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors

/** 列表缩略图边长 */
private val THUMBNAIL_SIZE = 56.dp

/** 列表缩略图的解码尺寸（px），56dp 在 xxhdpi 下约 170px，256 已留足余量 */
private const val THUMBNAIL_PIXELS = 256

/**
 * 文件列表项（卡片样式，支持左滑删除、右滑编辑、长按菜单）
 *
 * @param file 文件数据
 * @param displayPreview 要显示的预览内容（优先使用缓存，fallback 到数据库）
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 * @param onDelete 删除回调
 * @param onEdit 编辑回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: MarkdownFileMeta,
    displayPreview: String = file.preview,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val isDarkTheme = LocalFiberSurfaceColors.current.pageBackground.luminance() < 0.5f

    // 浏览页禁用滑动时直接渲染卡片，避免多余的手势层
    if (!swipeEnabled) {
        FileCard(
            file = file,
            displayPreview = displayPreview,
            onClick = onClick,
            onLongClick = onLongClick,
            isDarkTheme = isDarkTheme,
            modifier = modifier
        )
        return
    }

    SwipeableContainer(
        onSwipeLeft = onDelete,
        onSwipeRight = onEdit,
        swipeEnabled = swipeEnabled,
        modifier = modifier
    ) {
        FileCard(
            file = file,
            displayPreview = displayPreview,
            onClick = onClick,
            onLongClick = onLongClick,
            isDarkTheme = isDarkTheme
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCard(
    file: MarkdownFileMeta,
    displayPreview: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 9.dp)
            ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                val fileNameWithoutExt = file.name.removeSuffix(".md")
                val isQuickNote = isQuickNoteFileName(fileNameWithoutExt)

                // 标题（如果不是快速笔记则显示）
                if (!isQuickNote) {
                    Text(
                        text = fileNameWithoutExt,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 内容预览（使用传入的 displayPreview，可能来自缓存）
                if (displayPreview.isNotEmpty()) {
                    if (!isQuickNote) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = displayPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = PreviewConfig.MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 底部信息：只显示日期（左下角）
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FileUtils.formatDate(file.lastModified),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 有缩略图时图片本身就是"含图片"的信号；
                    // 只有尚未回填首图路径的旧数据才回退显示小图标
                    if (file.hasImage && file.firstImagePath.isBlank()) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "包含图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 右侧正方形缩略图：仅在有图且首图路径已索引时占位显示
            if (file.hasImage && file.firstImagePath.isNotBlank()) {
                Spacer(modifier = Modifier.width(10.dp))
                NoteImageThumbnail(
                    noteUri = file.uri,
                    imagePath = file.firstImagePath,
                    contentDescription = file.name,
                    modifier = Modifier.align(Alignment.Top)
                )
            }
            }
        }
}

/**
 * 笔记首图的正方形缩略图
 *
 * 图片 URI 由笔记 URI + 索引的首图路径零 IO 拼出（见 [UriHelper.resolveNoteImageUri]）；
 * 加载期间 ImageView 背景色即占位，加载失败（图片已被外部删除）保持占位色块
 */
@Composable
private fun NoteImageThumbnail(
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
                size(THUMBNAIL_PIXELS)
                crossfade(true)
            }
        },
        modifier = modifier
            .size(THUMBNAIL_SIZE)
            .clip(RoundedCornerShape(6.dp))
    )
}
