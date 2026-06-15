package com.bird.fiber.ui.screens.filelist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import timber.log.Timber

/**
 * 长按菜单 Bottom Sheet
 *
 * 从底部弹出，符合手指热区操作习惯
 * 包含：复制内容、重命名、编辑、删除、取消 五个选项
 *
 * @param isVisible 是否显示菜单
 * @param onDismiss 关闭菜单回调
 * @param onEdit 编辑回调
 * @param onDelete 删除回调
 * @param onCopy 复制回调
 * @param onRename 重命名回调
 * @param sheetState BottomSheet状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongPressMenuBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    onRename: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    if (!isVisible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        LongPressMenuContent(
            onDismiss = onDismiss,
            onEdit = onEdit,
            onDelete = onDelete,
            onCopy = onCopy,
            onRename = onRename,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Bottom Sheet 顶部的拖拽指示器
 */
@Composable
private fun BottomSheetDragHandle() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

/**
 * 长按菜单内容 - MD3 风格
 */
@Composable
private fun LongPressMenuContent(
    onDismiss: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    onRename: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 菜单标题
        Text(
            text = "笔记操作",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 复制内容按钮 - 卡片样式
        ActionButton(
            icon = Icons.Default.ContentCopy,
            text = "复制内容",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = {
                Timber.d("菜单点击：复制内容")
                onDismiss()
                onCopy()
            }
        )

        // 重命名按钮
        ActionButton(
            icon = Icons.Default.DriveFileRenameOutline,
            text = "重命名",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = {
                Timber.d("菜单点击：重命名")
                onDismiss()
                onRename()
            }
        )

        // 编辑按钮
        ActionButton(
            icon = Icons.Default.Edit,
            text = "编辑",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = {
                Timber.d("菜单点击：编辑")
                onDismiss()
                onEdit()
            }
        )

        // 删除按钮 - 红色高亮
        ActionButton(
            icon = Icons.Default.Delete,
            text = "删除",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            onClick = {
                Timber.d("菜单点击：删除")
                onDismiss()
                onDelete()
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 取消按钮 - 单独一行
        ActionButton(
            icon = null,
            text = "取消",
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = {
                Timber.d("菜单点击：取消")
                onDismiss()
            }
        )

        // 底部导航栏安全区域
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * 操作按钮 - 圆角卡片样式
 */
@Composable
private fun ActionButton(
    icon: ImageVector?,
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(0.9f)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor
        )
    }
}
