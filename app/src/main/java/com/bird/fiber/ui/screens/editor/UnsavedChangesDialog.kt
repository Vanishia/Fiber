package com.bird.fiber.ui.screens.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal fun UnsavedChangesDialog(
    onDismiss: () -> Unit,
    onSaveAndExit: () -> Unit,
    onExitWithoutSaving: () -> Unit,
    successColor: Color,
    errorColor: Color
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("未保存的修改") },
        text = { Text("你有未保存的修改，是否直接退出？") },
        dismissButton = {
            TextButton(onClick = onSaveAndExit) {
                Text("保存并退出", color = successColor)
            }
        },
        confirmButton = {
            TextButton(onClick = onExitWithoutSaving) {
                Text("不保存", color = errorColor)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

