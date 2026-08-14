package com.bird.fiber.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bird.fiber.data.config.PreviewConfig
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.utils.FileUtils

/**
 * "随便看看"预览弹窗——贴底弹出
 *
 * 点遮罩收起；右下角"换一条"（主题色文字按钮）切换下一条随机笔记；
 * 长文出现"展开全文"，点一下进入那篇笔记的编辑器页面
 *
 * @param memo 当前随机命中的笔记，null 表示库中暂时没有可用笔记
 * @param onDismiss 点击遮罩收起
 * @param onNext 换一条
 * @param onOpen 进入编辑器页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomMemoSheet(
    memo: MarkdownFileMeta?,
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
    memo: MarkdownFileMeta,
    onNext: () -> Unit,
    onOpen: (MarkdownFileMeta) -> Unit,
    modifier: Modifier = Modifier
) {
    // 预览达到上限说明还有更多内容，视为长文
    val isLongText = memo.preview.length >= PreviewConfig.MAX_CHARS

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = memo.name.removeSuffix(".md"),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // 元信息：所属库 · 修改时间 · 路径
        val metaText = buildString {
            if (memo.libraryName.isNotBlank()) {
                append(memo.libraryName)
                append(" · ")
            }
            append(FileUtils.formatDate(memo.lastModified))
            if (memo.path.isNotBlank()) {
                append(" · ")
                append(memo.path)
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

        Spacer(modifier = Modifier.height(12.dp))

        // 正文预览，折叠为 3 行；长文通过"展开全文"进入编辑器阅读
        if (memo.preview.isBlank()) {
            Text(
                text = "这条笔记是空的。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = memo.preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = PreviewConfig.MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 底部操作行：长文显示"展开全文"，右下角固定"换一条"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLongText) {
                TextButton(
                    onClick = { onOpen(memo) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("展开全文")
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onNext,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("换一条")
            }
        }

        // 底部导航栏安全区域
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
