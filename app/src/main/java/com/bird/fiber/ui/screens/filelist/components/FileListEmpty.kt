package com.bird.fiber.ui.screens.filelist.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 文件列表空状态组件
 *
 * @param searchQuery 当前搜索关键词，用于判断显示哪种空状态提示
 * @param modifier 修饰符
 */
@Composable
fun FileListEmpty(
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (searchQuery.isBlank())
                "文件夹中暂无Markdown文件"
            else
                "未找到匹配的文件",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
