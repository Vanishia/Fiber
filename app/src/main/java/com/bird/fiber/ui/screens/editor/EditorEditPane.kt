package com.bird.fiber.ui.screens.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import com.bird.fiber.ui.components.AssociationMenu
import com.bird.fiber.ui.components.findAssociationTrigger
import com.bird.fiber.ui.components.removeAssociationTrigger

@Composable
internal fun EditorEditPane(
    value: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit,
    onImageSelected: (String) -> Unit,
    isAddingImage: Boolean,
    topContentInset: Dp,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var associationMenuExpanded by remember { mutableStateOf(false) }
    var associationTriggerIndex by remember { mutableStateOf<Int?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onImageSelected(it.toString()) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                onContentChange(newValue)
                associationTriggerIndex = findAssociationTrigger(newValue)
                associationMenuExpanded = associationTriggerIndex != null
            },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topContentInset, bottom = bottomContentInset + 12.dp)
                ) {
                    innerTextField()
                }
            }
        )

        AssociationMenu(
            expanded = associationMenuExpanded,
            onDismiss = { associationMenuExpanded = false },
            onImageClick = {
                associationTriggerIndex?.let { index ->
                    onContentChange(removeAssociationTrigger(value, index))
                }
                associationMenuExpanded = false
                associationTriggerIndex = null
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )

        if (isAddingImage) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}
