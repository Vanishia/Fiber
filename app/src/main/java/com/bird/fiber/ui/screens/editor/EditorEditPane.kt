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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import com.bird.fiber.ui.components.AssociationMenu
import com.bird.fiber.ui.components.findAssociationTrigger
import com.bird.fiber.ui.components.removeAssociationTrigger
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
    // 否则刚进入编辑模式（选区默认在文末）会被误校正滚到最后一行。
    // 注意必须用 hasFocus：onFocusChanged 位于 BasicTextField 内部焦点目标的上游，
    // 聚焦时拿到的是 Active 状态（isFocused=false，hasFocus=true），用 isFocused 恒为 false
    var fieldFocused by remember { mutableStateOf(false) }
    // 校正函数在 LaunchedEffect 里异步执行，必须读最新值而非启动时的旧闭包
    val currentValue by rememberUpdatedState(value)
    // 点按产生的新选区要等 ViewModel 回传才反映到 value，至少晚一帧；
    // 本地同步记录选区供校正使用，否则首次聚焦时会按旧选区（初始在文末）把视图滚到末尾
    var localSelection by remember { mutableStateOf(value.selection) }
    // 区分校正产生的滚动和用户手动滚动，后者出现时聚焦校正循环立即退出
    var programmaticScroll by remember { mutableStateOf(false) }
    // 聚焦时的滚动锚点：点按发生的视图位置。聚焦稳定期内若被系统 stale 滚动带偏，
    // 优先恢复到这里，让用户点按的文字在屏幕上纹丝不动
    var anchorScroll by remember { mutableStateOf<Int?>(null) }
    // 是否处于聚焦后的稳定期（校正循环存活期间），稳定期外不使用锚点
    var focusSettling by remember { mutableStateOf(false) }
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
        val caret = localSelection.start.coerceIn(0, currentValue.text.length)
        val rect = layout.getCursorRect(caret).translate(Offset(x = 0f, y = topInsetPx))
        val visibleTop = scrollState.value.toFloat()
        val visibleBottom = visibleTop + viewportHeight.toFloat()
        // 底部留出两行高度的避让区：光标一旦进入键盘上方的两线区域就开始跟随滚动，
        // 每帧只补几个像素，视觉上随键盘平滑上移；
        // 若等光标撞上键盘线再补这两行，会一次性瞬移约 120px
        val keyboardMarginPx = rect.height * 2
        val revealBottom = visibleBottom - keyboardMarginPx
        if (rect.top >= visibleTop && rect.bottom <= revealBottom) return
        // 聚焦稳定期内可能被系统 stale 滚动带偏：优先回到锚点（点按时用户看到的位置）；
        // 锚点处光标会被键盘遮住时，从锚点出发做最小滚动（而不是从被带偏的位置算，
        // 否则会把光标贴到视口顶边，视觉上整页上移一个键盘高度）
        val anchor = anchorScroll
        val target = if (focusSettling && anchor != null) {
            val anchorTop = anchor.coerceIn(0, scrollState.maxValue).toFloat()
            val anchorRevealBottom = anchorTop + viewportHeight.toFloat() - keyboardMarginPx
            when {
                rect.top >= anchorTop && rect.bottom <= anchorRevealBottom -> anchorTop
                rect.bottom > anchorRevealBottom -> rect.bottom - viewportHeight.toFloat() + keyboardMarginPx
                else -> rect.top - cursorRevealMarginPx
            }
        } else if (rect.top < visibleTop) {
            rect.top - cursorRevealMarginPx
        } else {
            rect.bottom - viewportHeight.toFloat() + keyboardMarginPx
        }
        val coerced = target.roundToInt().coerceIn(0, scrollState.maxValue)
        if (coerced != scrollState.value) {
            scope.launch {
                programmaticScroll = true
                try {
                    scrollState.scrollTo(coerced)
                } finally {
                    programmaticScroll = false
                }
            }
        }
    }

    // 与 ViewModel 回传的选区保持同步（点按时已在 onValueChange 里先行更新，这里不会覆盖新值）
    LaunchedEffect(value.selection) { localSelection = value.selection }

    // 键盘弹起/收起改变可视高度时，BasicTextField 自带的跟随光标只在输入时触发，
    // 需要手动把光标滚回可视区。
    // ime/视口必须在 snapshotFlow 里读：在组合作用域读会让键盘动画的每一帧都
    // 触发整个面板重组，滚动肉眼可见地卡；流内读取则不触发重组。
    // 也不要在动画结束后再延迟补正：补正会以偏差目标再滚一次
    val imeInsets = WindowInsets.ime
    LaunchedEffect(Unit) {
        snapshotFlow { imeInsets.getBottom(density) to scrollState.viewportSize }
            .collect { revealCursorIfNeeded() }
    }

    // 聚焦瞬间系统会按聚焦时的旧选区（首次进入时是文末）发起 bringIntoView 动画，
    // 与点按产生的新选区存在竞争；且这次动画可能晚于键盘动画才发起。
    // 因此聚焦后至少校正 MIN_CORRECT_NANOS（覆盖键盘弹出延迟+动画+系统滚动尾部），
    // 先略过点按落键窗口（此时选区还是旧值），再逐帧校正直到滚动与键盘都稳定。
    // 最短窗口内无法区分系统滚动和用户滚动，宁可继续校正；窗口后用户手动滚动则立即退出
    LaunchedEffect(fieldFocused) {
        if (!fieldFocused) return@LaunchedEffect
        val startNanos = withFrameNanos { it }
        var stableFrames = 0
        var lastScrollValue = scrollState.value
        var lastImeBottom = imeInsets.getBottom(density)
        while (true) {
            val elapsed = withFrameNanos { it } - startNanos
            if (elapsed > FOCUS_CORRECT_TIMEOUT_NANOS) break
            if (elapsed >= MIN_CORRECT_NANOS) {
                if (scrollState.isScrollInProgress && !programmaticScroll) break
                if (stableFrames >= 8) break
            }
            if (elapsed > TAP_SETTLE_NANOS) {
                revealCursorIfNeeded()
            }
            withFrameNanos { }
            val scrollValue = scrollState.value
            val imeBottom = imeInsets.getBottom(density)
            stableFrames = if (scrollValue != lastScrollValue || imeBottom != lastImeBottom) 0 else stableFrames + 1
            lastScrollValue = scrollValue
            lastImeBottom = imeBottom
        }
        focusSettling = false
    }

    // 从预览切换到编辑时停留在相近位置：按滚动比例恢复（仅在进入时执行一次）。
    // 恢复后把仍是加载默认值（文末）的选区归位到可视区域中部：系统聚焦时会按
    // 选区发起 bringIntoView 动画，选区在文末会把整个视图拉向文末再被校正拉回
    // （视觉上"滚一下又被拽回来"）；归位后系统动画失去目标，用户点按会立即
    // 覆盖这个选区，不影响编辑
    LaunchedEffect(Unit) {
        // 等内容量出真实可滚动范围再换算目标位置
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        val fraction = initialScrollFraction ?: 0f
        if (fraction > 0f) {
            scrollState.scrollTo((fraction * scrollState.maxValue).roundToInt())
        }
        val layout = snapshotFlow { textLayoutResult }.first { it != null } ?: return@LaunchedEffect
        if (fieldFocused) return@LaunchedEffect  // 用户已点按，不动选区
        val text = currentValue.text
        if (text.isEmpty() || currentValue.selection.end != text.length) return@LaunchedEffect
        val centerY = (scrollState.value + scrollState.viewportSize / 2f - topInsetPx).coerceAtLeast(0f)
        val offset = layout.getOffsetForPosition(Offset(0f, centerY))
        onContentChange(TextFieldValue(text, TextRange(offset)))
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
                // 选区先行本地同步，光标校正不等 ViewModel 回传
                localSelection = newValue.selection
                onContentChange(newValue)
                associationTriggerIndex = findAssociationTrigger(newValue)
                associationMenuExpanded = associationTriggerIndex != null
            },
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged {
                    if (it.hasFocus && !fieldFocused) {
                        // 记录锚点：此刻的视图位置就是用户点按时看到的位置
                        anchorScroll = scrollState.value
                        focusSettling = true
                    } else if (!it.hasFocus) {
                        focusSettling = false
                    }
                    fieldFocused = it.hasFocus
                }
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

/** 聚焦后跳过校正的点按落键窗口：此时新选区尚未产生，按旧选区校正会误滚 */
private const val TAP_SETTLE_NANOS = 200_000_000L

/**
 * 聚焦校正循环的最短持续时间。键盘弹出有延迟，系统按旧选区发起的焦点滚动
 * 可能在键盘动画结束后才执行，窗口必须盖住整条链路，否则校正提前退出就没人纠正
 */
private const val MIN_CORRECT_NANOS = 1_000_000_000L

/** 聚焦校正循环的最长持续时间，防止异常情况下长驻 */
private const val FOCUS_CORRECT_TIMEOUT_NANOS = 2_500_000_000L
