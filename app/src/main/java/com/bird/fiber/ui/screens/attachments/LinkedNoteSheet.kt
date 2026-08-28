package com.bird.fiber.ui.screens.attachments

import android.graphics.Color
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.bird.fiber.utils.FileUtils

/** 正文不足这个行数时用空白补位，避免短文让弹窗缩得太矮 */
private const val MIN_PLACEHOLDER_LINES = 6

/** 正文超过这个渲染行数截断显示省略号，并出现"展开全文"按钮 */
private const val MAX_TRUNCATE_LINES = 15

/**
 * 多篇笔记链接同一附件时的选择菜单
 *
 * 每行显示文件名和摘要，点选后弹出对应笔记的预览
 */
@Composable
fun LinkedNoteChoicesDialog(
    notes: List<LinkedNote>,
    onSelect: (LinkedNote) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关联的笔记") },
        text = {
            LazyColumn {
                items(notes, key = { it.fileUri }) { note ->
                    Surface(
                        onClick = { onSelect(note) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = note.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (note.preview.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = note.preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 关联笔记预览弹窗——贴底弹出
 *
 * 正文最多展示 [MAX_TRUNCATE_LINES] 行，超出截断显示省略号，
 * 被截断时出现"展开全文"按钮，点一下进入该笔记的编辑器页面
 *
 * @param note 当前预览的笔记（content 为 null 时退回展示摘要）
 * @param onDismiss 点击遮罩收起
 * @param onOpen 进入编辑器页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedNoteSheet(
    note: LinkedNote,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        // 弹窗是独立 Window，默认把三键导航栏刷成白色不透明；
        // 这里处于弹窗内容内，才能通过 DialogWindowProvider 拿到弹窗自己的窗口，
        // 改回透明让导航栏透出弹窗内容，不遮挡按键
        val sheetView = LocalView.current
        DisposableEffect(sheetView) {
            val window = (sheetView.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
            }
            onDispose { }
        }

        // 阅读样式：正文比主屏幕列表大一号（bodyLarge），行间距再放宽一些
        val bodyStyle = MaterialTheme.typography.bodyLarge.let { style ->
            style.copy(lineHeight = style.fontSize * 1.6f)
        }

        // 出现省略号说明装不下，视为长文；换一篇后重置，避免上一篇的状态闪现
        var isLongText by remember(note) { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
        ) {
            Text(
                text = note.fileName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))

            val body = note.content ?: note.preview
            if (body.isBlank()) {
                Text(
                    text = "这篇笔记是空的。",
                    style = bodyStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = body,
                    style = bodyStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = MIN_PLACEHOLDER_LINES,
                    maxLines = MAX_TRUNCATE_LINES,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { isLongText = it.hasVisualOverflow }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isLongText) {
                        TextButton(
                            onClick = { onOpen(note.fileUri) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(start = 0.dp, end = 16.dp)
                        ) {
                            Text("展开全文")
                        }
                    }
                    if (note.lastModified > 0L) {
                        Text(
                            text = FileUtils.formatDate(note.lastModified),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 底部导航栏安全区域
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
