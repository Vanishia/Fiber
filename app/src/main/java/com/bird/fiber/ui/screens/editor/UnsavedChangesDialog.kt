package com.bird.fiber.ui.screens.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun UnsavedChangesDialog(
    onDismiss: () -> Unit,
    onSaveAndExit: () -> Unit,
    onExitWithoutSaving: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("未保存的修改") },
        text = { Text("要保存修改后退出吗？") },
        dismissButton = {
            TextButton(onClick = onExitWithoutSaving) {
                Text("不保存")
            }
        },
        confirmButton = {
            Button(onClick = onSaveAndExit) {
                Text("保存")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
