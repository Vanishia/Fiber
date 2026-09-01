package com.bird.fiber.ui.screens.filelist.components

import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 重命名文件对话框组件
 *
 * @param currentName 当前文件名（不包含.md扩展名）
 * @param newName 新文件名输入值
 * @param onNewNameChange 文件名变化回调
 * @param onDismiss 关闭对话框回调
 * @param onConfirm 确认重命名回调
 * @param selectAllOnOpen 打开时自动聚焦并全选输入框内容
 * @param confirmOnDismiss 点击遮罩（对话框外部）时直接触发确认而不是取消，
 *                         此模式下不显示标题与底部按钮，键盘完成键同样触发确认
 */
@Composable
fun RenameFileDialog(
    currentName: String,
    newName: String,
    onNewNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    selectAllOnOpen: Boolean = false,
    confirmOnDismiss: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    // 内部使用 TextFieldValue 以便控制光标位置与全选
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = newName,
                selection = if (selectAllOnOpen) TextRange(0, newName.length) else TextRange(newName.length)
            )
        )
    }

    Dialog(onDismissRequest = if (confirmOnDismiss) onConfirm else onDismiss) {
        // Dialog 内容里的 LocalView 才是 Dialog 自己的 View
        val view = LocalView.current
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
                if (!confirmOnDismiss) {
                    // 标题
                    Text(
                        text = "重命名笔记",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 显示当前文件名
                Text(
                    text = "当前名称: $currentName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 输入框
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        onNewNameChange(it.text)
                    },
                    label = { Text("新名称") },
                    placeholder = { Text("输入新文件名...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (confirmOnDismiss) onConfirm() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                if (!confirmOnDismiss) {
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

        LaunchedEffect(selectAllOnOpen) {
            if (selectAllOnOpen) {
                // Dialog 默认不弹软键盘，需要显式设置 softInputMode 再请求焦点
                (view.parent as? DialogWindowProvider)?.window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                )
                focusRequester.requestFocus()
            }
        }
    }
}
