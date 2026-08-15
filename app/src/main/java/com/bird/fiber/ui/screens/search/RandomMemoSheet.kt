package com.bird.fiber.ui.screens.search

import android.graphics.Color
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.ui.screens.filelist.RandomMemo
import com.bird.fiber.utils.FileUtils

/**
 * 快速笔记的自动命名——GenerateFileNameUseCase 生成的纯时间戳（yy-MM-dd_HH-mm-ss）
 *
 * 这种文件名没有可读性，弹窗顶部不显示标题，直接从正文开始
 */
private val AUTO_NAME_PATTERN = Regex("""^\d{2}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}$""")

/** 正文不足这个行数时用空白补位，避免短文让弹窗缩得太矮 */
private const val MIN_PLACEHOLDER_LINES = 8

/** 正文超过这个渲染行数截断显示省略号，并出现"展开全文"按钮 */
private const val MAX_TRUNCATE_LINES = 15

/**
 * "随机漫步"预览弹窗——贴底弹出
 *
 * 点遮罩收起；正文最多展示 [MAX_TRUNCATE_LINES] 行，超出截断显示省略号，
 * 被截断时出现"展开全文"按钮，点一下进入那篇笔记的编辑器页面；
 * 右下角灰色切换图标换下一条随机笔记，"库名 · 日期"在展开按钮下方
 *
 * @param memo 当前随机命中的笔记（含全文），null 表示库中暂时没有可用笔记
 * @param onDismiss 点击遮罩收起
 * @param onNext 换一条
 * @param onOpen 进入编辑器页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomMemoSheet(
    memo: RandomMemo?,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    onOpen: (MarkdownFileMeta) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    if (memo == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        // 弹窗是独立 Window，默认把三键导航栏刷成白色不透明；
        // 这里处于弹窗内容内，才能通过 DialogWindowProvider 拿到弹窗自己的窗口，
        // 改回透明让导航栏透出弹窗内容，不遮挡按键
        val sheetView = LocalView.current
        DisposableEffect(sheetView) {
            val window = (sheetView.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
            }
            onDispose { }
        }

        // 打开弹窗时内容淡入并轻微上移，配合弹窗自身的上滑，
        // 让搜索页到随机漫步之间有一段过渡而不是生硬闪现
        val contentVisible = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(
            visibleState = contentVisible,
            enter = fadeIn(animationSpec = tween(220)) +
                slideInVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 10 }
                ),
            // 收起时弹窗整体下滑，内容无需再播退出动画
            exit = ExitTransition.None
        ) {
            // "换一条"：新旧笔记交叉淡入淡出并轻微上移，替代瞬间替换
            AnimatedContent(
                targetState = memo,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) +
                        slideInVertically(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            initialOffsetY = { it / 12 }
                        ))
                        .togetherWith(fadeOut(animationSpec = tween(150)))
                },
                label = "random-memo-switch"
            ) { currentMemo ->
                RandomMemoContent(
                    memo = currentMemo,
                    onNext = onNext,
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RandomMemoContent(
    memo: RandomMemo,
    onNext: () -> Unit,
    onOpen: (MarkdownFileMeta) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName = memo.meta.name.removeSuffix(".md")
    val showTitle = !AUTO_NAME_PATTERN.matches(displayName)

    // 阅读样式：正文比主屏幕列表大一号（bodyLarge），行间距再放宽一些
    val bodyStyle = MaterialTheme.typography.bodyLarge.let { style ->
        style.copy(lineHeight = style.fontSize * 1.6f)
    }

    // 出现省略号说明 12 行装不下，视为长文；换一条后重置，避免上一条的状态闪现
    var isLongText by remember(memo) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
    ) {
        // 标题：快速笔记的自动命名不显示；颜色用主题色
        if (showTitle) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            // 无标题时正文离弹窗顶部留出一点呼吸空间
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 正文：不足 8 行用空白补位，超过 12 行像摘要一样截断显示省略号
        if (memo.content.isBlank()) {
            Text(
                text = "这条笔记是空的。",
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = memo.content,
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = MIN_PLACEHOLDER_LINES,
                maxLines = MAX_TRUNCATE_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { isLongText = it.hasVisualOverflow }
            )
        }

        // 底部操作区：左侧"展开全文"在上、"库名 · 日期"在下，右侧灰色切换图标
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isLongText) {
                    TextButton(
                        onClick = { onOpen(memo.meta) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(start = 0.dp, end = 16.dp)
                    ) {
                        Text("展开全文")
                    }
                }
                Text(
                    text = buildString {
                        if (memo.meta.libraryName.isNotBlank()) {
                            append(memo.meta.libraryName)
                            append(" · ")
                        }
                        append(FileUtils.formatDate(memo.meta.lastModified))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "换一条",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 底部导航栏安全区域
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
