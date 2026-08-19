package com.bird.fiber.ui.screens.sidebar.heatmap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bird.fiber.domain.heatmap.HeatmapDay
import com.bird.fiber.domain.heatmap.NoteHeatmapAggregator
import java.time.LocalDate

/**
 * 侧边栏记录热力图区块（自带 ViewModel）
 *
 * 点击色块 → [onDayClick]（定位到当天的笔记）；
 * 点击色块之外的区域 → [onClick]（进入热力图说明页）
 */
@Composable
fun NoteHeatmapSection(
    onDayClick: (LocalDate) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HeatmapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NoteHeatmap(
        uiState = uiState,
        onDayClick = onDayClick,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteHeatmap(
    uiState: HeatmapUiState,
    onDayClick: (LocalDate) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        if (!uiState.isLoaded) {
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEATMAP_GRID_HEIGHT_DP.dp)
            )
            return@Column
        }

        // 月份标签行：某周包含当月 1 号时在该列上方标注
        Row(horizontalArrangement = Arrangement.spacedBy(CELL_SPACING_DP.dp)) {
            uiState.weeks.forEach { week ->
                val monthStart = week.firstOrNull { it.date.dayOfMonth == 1 }
                Box(modifier = Modifier.width(CELL_SIZE_DP.dp)) {
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
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(CELL_SPACING_DP.dp)) {
            uiState.weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(CELL_SPACING_DP.dp)) {
                    week.forEach { day ->
                        HeatmapCell(day = day, maxCount = uiState.maxCount, onDayClick = onDayClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    day: HeatmapDay,
    maxCount: Int,
    onDayClick: (LocalDate) -> Unit
) {
    if (day.isFuture) {
        // 未来日期占位，保持网格对齐
        Spacer(modifier = Modifier.size(CELL_SIZE_DP.dp))
        return
    }

    val color = cellColor(day.count, maxCount)
    Box(
        modifier = Modifier
            .size(CELL_SIZE_DP.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
            .clickable { onDayClick(day.date) }
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
private val HEATMAP_GRID_HEIGHT_DP = CELL_SIZE_DP * 7 + CELL_SPACING_DP * 6
