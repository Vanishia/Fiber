package com.bird.fiber.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

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
    anchorBounds: Rect,
    onDismiss: () -> Unit,
    onImageClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = anchorBounds.left.roundToInt(),
                    y = anchorBounds.bottom.roundToInt()
                )
            }
            .size(1.dp)
    ) {
        DropdownMenu(
            expanded = expanded && anchorBounds != Rect.Zero,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = false)
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
}
