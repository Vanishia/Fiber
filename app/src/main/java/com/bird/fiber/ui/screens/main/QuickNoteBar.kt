package com.bird.fiber.ui.screens.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bird.fiber.data.model.Attachment
import com.bird.fiber.ui.components.AssociationMenu
import com.bird.fiber.ui.components.findAssociationTrigger
import com.bird.fiber.ui.components.removeAssociationTrigger

@Composable
fun QuickNoteBar(
    content: String,
    attachments: List<Attachment>,
    isSaving: Boolean,
    isAddingImage: Boolean,
    error: String?,
    onContentChange: (String) -> Unit,
    onImageSelected: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onDismissError: () -> Unit,
    onSaveClick: () -> Unit
) {
    val density = LocalDensity.current
    var isInputFocused by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue(content)) }
    var associationMenuExpanded by remember { mutableStateOf(false) }
    var associationTriggerIndex by remember { mutableStateOf<Int?>(null) }
    var cursorBounds by remember { mutableStateOf(Rect.Zero) }
    var inputSize by remember { mutableStateOf(IntSize.Zero) }
    val isContentEmpty = content.isBlank() && attachments.isEmpty()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onImageSelected(it.toString()) }
    }

    androidx.compose.runtime.LaunchedEffect(content) {
        if (content != value.text) {
            value = TextFieldValue(content, TextRange(content.length))
        }
    }

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {
        if (error != null) {
            Snackbar(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                action = {
                    TextButton(onClick = onDismissError) {
                        Text("关闭")
                    }
                }
            ) {
                Text(error)
            }
        }

        if (attachments.isNotEmpty() || isAddingImage) {
            AttachmentStrip(
                attachments = attachments,
                isAddingImage = isAddingImage,
                onRemoveAttachment = onRemoveAttachment,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                .padding(horizontal = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (isInputFocused) 2.dp else 1.dp,
                    color = if (isInputFocused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { inputSize = it }
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { newValue ->
                        value = newValue
                        onContentChange(newValue.text)
                        associationTriggerIndex = findAssociationTrigger(newValue)
                        associationMenuExpanded = associationTriggerIndex != null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isInputFocused = it.isFocused }
                        .padding(
                            start = 16.dp,
                            top = 16.dp,
                            end = 52.dp,
                            bottom = 16.dp
                        ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    minLines = if (isInputFocused) 3 else 1,
                    maxLines = 6,
                    onTextLayout = { layoutResult ->
                        val caret = value.selection.start.coerceIn(0, value.text.length)
                        val contentInsetPx = with(density) { 16.dp.toPx() }
                        val measuredBounds = layoutResult.getCursorRect(caret).translate(
                            Offset(x = contentInsetPx, y = contentInsetPx)
                        )
                        val visibleBottom = if (inputSize.height > 0) {
                            measuredBounds.bottom.coerceAtMost(inputSize.height.toFloat())
                        } else {
                            measuredBounds.bottom
                        }
                        cursorBounds = measuredBounds.translate(
                            Offset(
                                x = 0f,
                                y = visibleBottom - measuredBounds.bottom
                            )
                        )
                    },
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.text.isEmpty()) {
                                Text(
                                    text = "快速记录…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            AssociationMenu(
                expanded = associationMenuExpanded,
                anchorBounds = cursorBounds,
                onDismiss = { associationMenuExpanded = false },
                onImageClick = {
                    associationTriggerIndex?.let { index ->
                        value = removeAssociationTrigger(value, index)
                        onContentChange(value.text)
                    }
                    associationMenuExpanded = false
                    associationTriggerIndex = null
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )

            if (!isContentEmpty || isInputFocused) {
                FilledIconButton(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp)
                        .size(32.dp),
                    enabled = !isContentEmpty && !isSaving && !isAddingImage,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "发送",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<Attachment>,
    isAddingImage: Boolean,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(attachments, key = { it.uri }) { attachment ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 220.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = attachment.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRemoveAttachment(attachment.relativePath) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "移除图片",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        if (isAddingImage) {
            item {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}
