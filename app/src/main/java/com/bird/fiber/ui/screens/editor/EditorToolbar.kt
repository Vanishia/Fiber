package com.bird.fiber.ui.screens.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp

@Composable
internal fun EditorToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    enabled: Boolean,
    canAddImage: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: (EditorFormat) -> Unit,
    onImage: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).horizontalScroll(rememberScrollState())) {
            // 工具点击不夺取文本输入焦点，保留软键盘和选区。
            IconButton(onClick = onUndo, enabled = enabled && canUndo, modifier = Modifier.focusProperties { canFocus = false }) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
            }
            IconButton(onClick = onRedo, enabled = enabled && canRedo, modifier = Modifier.focusProperties { canFocus = false }) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "重做")
            }
            listOf(
                "标题" to EditorFormat.Heading,
                "加粗" to EditorFormat.Bold,
                "列表" to EditorFormat.BulletList,
                "任务" to EditorFormat.TaskList,
                "代码块" to EditorFormat.CodeBlock,
                "引用" to EditorFormat.Quote,
                "斜体" to EditorFormat.Italic
            ).forEach { (label, format) ->
                TextButton(onClick = { onFormat(format) }, enabled = enabled, modifier = Modifier.focusProperties { canFocus = false }) {
                    Text(label)
                }
            }
            IconButton(onClick = onImage, enabled = enabled && canAddImage, modifier = Modifier.focusProperties { canFocus = false }) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "插入图片")
            }
        }
    }
}
