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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import com.bird.fiber.ui.components.AssociationMenu
import com.bird.fiber.ui.components.findAssociationTrigger
import com.bird.fiber.ui.components.removeAssociationTrigger
import kotlin.math.roundToInt

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
    val density = LocalDensity.current
    var associationMenuExpanded by remember { mutableStateOf(false) }
    var associationTriggerIndex by remember { mutableStateOf<Int?>(null) }
    var cursorBounds by remember { mutableStateOf(Rect.Zero) }
    // 光标在滚动内容坐标系中的位置（含顶部 inset），用于光标可见性兜底
    var cursorContentRect by remember { mutableStateOf<Rect?>(null) }
    val topInsetPx = with(density) { topContentInset.toPx() }
    val cursorRevealMarginPx = with(density) { 8.dp.toPx() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onImageSelected(it.toString()) }
    }

    // 键盘弹起/收起会改变可视区域高度，而 BasicTextField 自带的跟随光标
    // 只在输入时触发；长文下键盘弹起后光标可能被甩出屏幕（界面停在底部、
    // 光标还在顶部），这里兜底把光标滚回可视范围
    LaunchedEffect(scrollState.viewportSize, value.selection, cursorContentRect) {
        val rect = cursorContentRect ?: return@LaunchedEffect
        val viewportHeight = scrollState.viewportSize
        if (viewportHeight <= 0) return@LaunchedEffect
        val visibleTop = scrollState.value.toFloat()
        val visibleBottom = visibleTop + viewportHeight.toFloat()
        val target = when {
            rect.top < visibleTop -> rect.top - cursorRevealMarginPx
            rect.bottom > visibleBottom -> rect.bottom - viewportHeight.toFloat() + cursorRevealMarginPx
            else -> return@LaunchedEffect
        }
        scrollState.scrollTo(target.roundToInt().coerceIn(0, scrollState.maxValue))
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
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                // 与预览视图共用的行距倍数，保证两种模式排版一致
                lineHeight = MaterialTheme.typography.bodyLarge.fontSize * BODY_LINE_HEIGHT_MULTIPLIER
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            onTextLayout = { layoutResult ->
                val caret = value.selection.start.coerceIn(0, value.text.length)
                val rect = layoutResult.getCursorRect(caret)
                val contentRect = rect.translate(Offset(x = 0f, y = topInsetPx))
                cursorContentRect = contentRect
                cursorBounds = contentRect.translate(Offset(x = 0f, y = -scrollState.value.toFloat()))
            },
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
            anchorBounds = cursorBounds,
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
