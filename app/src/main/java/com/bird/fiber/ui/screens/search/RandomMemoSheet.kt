package com.bird.fiber.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.ui.screens.filelist.RandomMemo
import com.bird.fiber.utils.FileUtils

/**
 * 快速笔记的自动命名——GenerateFileNameUseCase 生成的纯时间戳（yy-MM-dd_HH-mm-ss）
 *
 * 这种文件名没有可读性，弹窗顶部不显示标题，直接从正文开始
 */
private val AUTO_NAME_PATTERN = Regex("""^\d{2}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}$""")

/** 正文实际渲染满 10 行视为长文，出现"展开"按钮 */
private const val LONG_TEXT_MIN_LINES = 10

/**
 * "随机漫步"预览弹窗——贴底弹出
 *
 * 点遮罩收起；正文完整展示、内部可滚动；
 * 渲染满 [LONG_TEXT_MIN_LINES] 行出现"展开"按钮，点一下进入那篇笔记的编辑器页面；
 * 右下角灰色切换图标换下一条随机笔记
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
        RandomMemoContent(
            memo = memo,
            onNext = onNext,
            onOpen = onOpen,
            modifier = Modifier.fillMaxWidth()
        )
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

    // 阅读样式：正文比主屏幕列表大一号（bodyLarge），行间距再放宽一些
    val bodyStyle = MaterialTheme.typography.bodyLarge.let { style ->
        style.copy(lineHeight = style.fontSize * 1.6f)
    }

    // 用真实排版结果数行数，能正确处理自动换行、空行、长段落等情况；
    // 换一条后重置，避免上一条的行数闪现"展开"按钮
    var renderedLineCount by remember(memo) { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        // 标题：快速笔记的自动命名不显示；颜色用主题色的深色调（onPrimaryContainer）
        if (!AUTO_NAME_PATTERN.matches(displayName)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 元信息：所属库 · 修改时间 · 路径
        val metaText = buildString {
            if (memo.meta.libraryName.isNotBlank()) {
                append(memo.meta.libraryName)
                append(" · ")
            }
            append(FileUtils.formatDate(memo.meta.lastModified))
            if (memo.meta.path.isNotBlank()) {
                append(" · ")
                append(memo.meta.path)
            }
        }
        Text(
            text = metaText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 正文全文，超长时在弹窗内滚动；短文时弹窗自适应收缩
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
                onTextLayout = { renderedLineCount = it.lineCount },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
            )
        }

        // 底部操作行：长文显示"展开"，右下角固定灰色切换图标
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (renderedLineCount >= LONG_TEXT_MIN_LINES) {
                TextButton(
                    onClick = { onOpen(memo.meta) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("展开")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(40.dp)
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
