package com.bird.fiber.ui.screens.filelist.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 重命名文件对话框组件
 *
 * @param currentName 当前文件名（不包含.md扩展名）
 * @param newName 新文件名输入值
 * @param onNewNameChange 文件名变化回调
 * @param onDismiss 关闭对话框回调
 * @param onConfirm 确认重命名回调
 */
@Composable
fun RenameFileDialog(
    currentName: String,
    newName: String,
    onNewNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题
                Text(
                    text = "重命名笔记",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 显示当前文件名
                Text(
                    text = "当前名称: $currentName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 输入框
                OutlinedTextField(
                    value = newName,
                    onValueChange = onNewNameChange,
                    label = { Text("新名称") },
                    placeholder = { Text("输入新文件名...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = newName.isNotBlank() && newName != currentName
                    ) {
                        Text("重命名")
                    }
                }
            }
        }
    }
}
