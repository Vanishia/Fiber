package com.bird.fiber.ui.screens.heatmap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bird.fiber.ui.components.FloatingBackTopBar
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors

/**
 * 记录热力图说明页（占位，具体内容后续补充）
 */
@Composable
fun HeatmapScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LocalFiberSurfaceColors.current.pageBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "热力图统计每个笔记归属的日期：快速笔记按文件名中的时间戳计入创建当天，" +
                    "其余笔记按最后修改时间计入；颜色越深表示当天的笔记越多。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, end = 20.dp, top = 120.dp)
            )

            FloatingBackTopBar(
                title = "记录热力图",
                onBackClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )
        }
    }
}
