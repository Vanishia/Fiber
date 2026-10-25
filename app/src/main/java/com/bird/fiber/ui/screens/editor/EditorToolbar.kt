package com.bird.fiber.ui.screens.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bird.fiber.R

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
            EditorToolbarButton(Icons.AutoMirrored.Filled.Undo, R.string.editor_toolbar_undo, enabled && canUndo, onUndo)
            EditorToolbarButton(Icons.AutoMirrored.Filled.Redo, R.string.editor_toolbar_redo, enabled && canRedo, onRedo)
            listOf(
                Triple(Icons.Default.Title, R.string.editor_toolbar_heading, EditorFormat.Heading),
                Triple(Icons.Default.FormatBold, R.string.editor_toolbar_bold, EditorFormat.Bold),
                Triple(Icons.AutoMirrored.Filled.FormatListBulleted, R.string.editor_toolbar_bullet_list, EditorFormat.BulletList),
                Triple(Icons.Default.CheckBox, R.string.editor_toolbar_task_list, EditorFormat.TaskList),
                Triple(Icons.Default.Code, R.string.editor_toolbar_code_block, EditorFormat.CodeBlock),
                Triple(Icons.Default.FormatQuote, R.string.editor_toolbar_quote, EditorFormat.Quote),
                Triple(Icons.Default.FormatItalic, R.string.editor_toolbar_italic, EditorFormat.Italic)
            ).forEach { (icon, labelRes, format) ->
                EditorToolbarButton(icon, labelRes, enabled) {
                    onFormat(format)
                }
            }
            EditorToolbarButton(Icons.Default.AddPhotoAlternate, R.string.editor_toolbar_image, enabled && canAddImage, onImage)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorToolbarButton(
    icon: ImageVector,
    @StringRes labelRes: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(labelRes)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        focusable = false
    ) {
        // 工具点击和长按提示不夺取文本输入焦点，保留软键盘和选区。
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.focusProperties { canFocus = false }
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}
