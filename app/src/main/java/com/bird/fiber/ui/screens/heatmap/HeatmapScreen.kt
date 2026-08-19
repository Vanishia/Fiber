package com.bird.fiber.ui.screens.heatmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bird.fiber.domain.heatmap.HeatmapDay
import com.bird.fiber.domain.heatmap.NoteHeatmapAggregator
import com.bird.fiber.ui.components.FloatingBackTopBar
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors

/**
 * 记录热力图页：默认展示最近一年的全量热力图（可左右滑动），
 * 色块越深得当天笔记越多；说明文字在热力图下方
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeatmapScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HeatmapPageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LocalFiberSurfaceColors.current.pageBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 96.dp)
            ) {
                if (!uiState.isLoaded) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HEATMAP_TOTAL_HEIGHT_DP.dp)
                    )
                } else {
                    ScrollableWeeksHeatmap(
                        weeks = uiState.weeks,
                        maxCount = uiState.maxCount,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "热力图统计每个笔记归属的日期：快速笔记按文件名中的时间戳计入创建当天，" +
                        "其余笔记按最后修改时间计入；颜色越深表示当天的笔记越多。" +
                        "左右滑动可以查看最近一年的记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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

/**
 * 横向可滑动的周网格热力图（一列一周，周一在首行）
 *
 * 采用反向布局：最新一周贴右端，初始即可看到今天，向左滑回看更早的日期
 */
@Composable
private fun ScrollableWeeksHeatmap(
    weeks: List<List<HeatmapDay>>,
    maxCount: Int,
    modifier: Modifier = Modifier
) {
    val reversedWeeks = weeks.asReversed()
    LazyRow(
        modifier = modifier.height(HEATMAP_TOTAL_HEIGHT_DP.dp),
        reverseLayout = true,
        horizontalArrangement = Arrangement.spacedBy(CELL_SPACING_DP.dp)
    ) {
        items(reversedWeeks.size) { index ->
            val week = reversedWeeks[index]
            Column {
                // 月份标签：某周包含当月 1 号时在该列上方标注
                val monthStart = week.firstOrNull { it.date.dayOfMonth == 1 }
                Box(
                    modifier = Modifier
                        .width(CELL_SIZE_DP.dp)
                        .height(MONTH_LABEL_HEIGHT_DP.dp)
                ) {
                    if (monthStart != null) {
                        Text(
                            text = "${monthStart.date.monthValue}月",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.requiredWidth(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(CELL_SPACING_DP.dp)) {
                    week.forEach { day ->
                        HeatmapCell(day = day, maxCount = maxCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(day: HeatmapDay, maxCount: Int) {
    if (day.isFuture) {
        // 未来日期占位，保持网格对齐
        Spacer(modifier = Modifier.size(CELL_SIZE_DP.dp))
        return
    }
    Box(
        modifier = Modifier
            .size(CELL_SIZE_DP.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(cellColor(day.count, maxCount))
    )
}

@Composable
private fun cellColor(count: Int, maxCount: Int): Color {
    val level = NoteHeatmapAggregator.colorLevel(count, maxCount)
    if (level == 0) {
        return MaterialTheme.colorScheme.surfaceVariant
    }
    return MaterialTheme.colorScheme.primary.copy(alpha = LEVEL_ALPHAS[level - 1])
}

private val LEVEL_ALPHAS = floatArrayOf(0.25f, 0.45f, 0.7f, 1f)
private const val CELL_SIZE_DP = 13
private const val CELL_SPACING_DP = 3
private const val MONTH_LABEL_HEIGHT_DP = 14
private val HEATMAP_TOTAL_HEIGHT_DP =
    MONTH_LABEL_HEIGHT_DP + 4 + CELL_SIZE_DP * 7 + CELL_SPACING_DP * 6
