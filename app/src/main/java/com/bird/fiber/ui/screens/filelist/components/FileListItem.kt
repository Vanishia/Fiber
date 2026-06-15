package com.bird.fiber.ui.screens.filelist.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bird.fiber.data.config.PreviewConfig
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.utils.FileUtils

/**
 * 判断文件名是否为快速笔记的日期格式（yy-mm-dd_hh-mm-ss）
 * 匹配格式：26-01-29_02-38-10
 */
private val QUICK_NOTE_PATTERN = Regex("^\\d{2}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}$")

fun isQuickNoteFileName(fileName: String): Boolean {
    return QUICK_NOTE_PATTERN.matches(fileName)
}

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
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    SwipeableContainer(
        onSwipeLeft = onDelete,
        onSwipeRight = onEdit
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 9.dp)
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
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = FileUtils.formatDate(file.lastModified),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
