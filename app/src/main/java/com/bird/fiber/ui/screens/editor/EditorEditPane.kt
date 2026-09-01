package com.bird.fiber.ui.screens.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import com.bird.fiber.ui.components.AssociationMenu
import com.bird.fiber.ui.components.findAssociationTrigger
import com.bird.fiber.ui.components.removeAssociationTrigger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun EditorEditPane(
    value: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit,
    onImageSelected: (String) -> Unit,
    isAddingImage: Boolean,
    topContentInset: Dp,
    bottomContentInset: Dp,
    initialScrollFraction: Float? = null,
    onScrollFractionChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var associationMenuExpanded by remember { mutableStateOf(false) }
    var associationTriggerIndex by remember { mutableStateOf<Int?>(null) }
    var cursorBounds by remember { mutableStateOf(Rect.Zero) }
    // 最近一次文本布局结果；键盘弹起等视口变化时按"当前"光标位置校正滚动，
    // 不能直接缓存光标矩形（onTextLayout 不随纯选区变化回调，缓存会过期）
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    // 光标只在聚焦时显示，光标校正也仅限聚焦后；
    // 否则刚进入编辑模式（选区默认在文末）会被误校正滚到最后一行
    var fieldFocused by remember { mutableStateOf(false) }
    val topInsetPx = with(density) { topContentInset.toPx() }
    val cursorRevealMarginPx = with(density) { 8.dp.toPx() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onImageSelected(it.toString()) }
    }

    // 光标不在可视范围时把它滚回来；光标可见时不做任何事，不与手动滚动打架
    fun revealCursorIfNeeded() {
        if (!fieldFocused) return
        val layout = textLayoutResult ?: return
        val viewportHeight = scrollState.viewportSize
        if (viewportHeight <= 0) return
        val caret = value.selection.start.coerceIn(0, value.text.length)
        val rect = layout.getCursorRect(caret).translate(Offset(x = 0f, y = topInsetPx))
        val visibleTop = scrollState.value.toFloat()
        val visibleBottom = visibleTop + viewportHeight.toFloat()
        val target = when {
            rect.top < visibleTop -> rect.top - cursorRevealMarginPx
            rect.bottom > visibleBottom -> rect.bottom - viewportHeight.toFloat() + cursorRevealMarginPx
            else -> return
        }
        val coerced = target.roundToInt().coerceIn(0, scrollState.maxValue)
        if (coerced != scrollState.value) {
            scope.launch { scrollState.scrollTo(coerced) }
        }
    }

    // 键盘弹起/收起改变可视高度时，BasicTextField 自带的跟随光标只在输入时触发，
    // 系统侧还会尝试把焦点区域滚进屏幕，长文下会错误地滚到最后一行；
    // 动画期间逐帧校正，并在动画结束后再延迟补一次，确保最终停在光标处
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    LaunchedEffect(scrollState.viewportSize, value.selection) {
        revealCursorIfNeeded()
        delay(300)
        revealCursorIfNeeded()
    }
    LaunchedEffect(imeBottomPx) {
        revealCursorIfNeeded()
    }

    // 从预览切换到编辑时停留在相近位置：按滚动比例恢复（仅在进入时执行一次）
    LaunchedEffect(Unit) {
        val fraction = initialScrollFraction ?: return@LaunchedEffect
        if (fraction <= 0f) return@LaunchedEffect
        // 等内容量出真实可滚动范围再换算目标位置
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        scrollState.scrollTo((fraction * scrollState.maxValue).roundToInt())
    }

    // 上报滚动比例，供切回预览时恢复位置
    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.value to scrollState.maxValue }.collect { (offset, max) ->
            if (max > 0) onScrollFractionChanged(offset / max.toFloat())
        }
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
                .onFocusChanged { fieldFocused = it.isFocused }
                .verticalScroll(scrollState),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                // 与预览视图共用的行距倍数，保证两种模式排版一致
                lineHeight = MaterialTheme.typography.bodyLarge.fontSize * BODY_LINE_HEIGHT_MULTIPLIER
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            onTextLayout = { layoutResult ->
                textLayoutResult = layoutResult
                val caret = value.selection.start.coerceIn(0, value.text.length)
                val rect = layoutResult.getCursorRect(caret)
                cursorBounds = rect.translate(
                    Offset(x = 0f, y = topInsetPx - scrollState.value)
                )
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
