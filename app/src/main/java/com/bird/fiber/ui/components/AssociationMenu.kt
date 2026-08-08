package com.bird.fiber.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

fun findAssociationTrigger(value: TextFieldValue): Int? {
    if (!value.selection.collapsed || value.selection.start == 0) return null
    val index = value.selection.start - 1
    if (value.text.getOrNull(index) != '@') return null
    return index.takeIf { it == 0 || value.text[it - 1].isWhitespace() }
}

fun removeAssociationTrigger(value: TextFieldValue, index: Int): TextFieldValue {
    if (value.text.getOrNull(index) != '@') return value
    val updated = value.text.removeRange(index, index + 1)
    return value.copy(text = updated, selection = TextRange(index))
}

@Composable
fun AssociationMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onImageClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("图片") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null
                )
            },
            onClick = onImageClick
        )
    }
}
