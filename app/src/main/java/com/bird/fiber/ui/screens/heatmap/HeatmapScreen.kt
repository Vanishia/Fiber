package com.bird.fiber.ui.screens.heatmap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bird.fiber.domain.heatmap.HeatmapDay
import com.bird.fiber.domain.heatmap.NoteHeatmapAggregator
import com.bird.fiber.ui.components.FloatingBackTopBar
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors
import java.time.LocalDate
import java.time.YearMonth

/**
 * 记录热力图页
 *
 * 默认展示最近一年的全量热力图（可左右滑动）；
 * 顶栏右侧筛选菜单：全部笔记 / 选择月份（单月日历视图）/ 选择年份（整年周视图）；
 * 说明文字在热力图下方
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeatmapScreen(
    onBackClick: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HeatmapPageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var pickerMode by remember { mutableStateOf<HeatmapPickerMode?>(null) }
    val today = remember { LocalDate.now() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LocalFiberSurfaceColors.current.pageBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 96.dp)
            ) {
                // 当前筛选范围说明
                Text(
                    text = filterCaption(uiState.filter),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                when {
                    !uiState.isLoaded -> LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WEEKS_HEATMAP_HEIGHT_DP.dp)
                    )

                    uiState.isSwitching -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WEEKS_HEATMAP_HEIGHT_DP.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }

                    uiState.filter is HeatmapFilter.Month -> MonthCalendarGrid(
                        rows = uiState.monthRows,
                        maxCount = uiState.maxCount,
                        onDayClick = onDayClick,
                        modifier = Modifier.fillMaxWidth()
                    )

                    else -> {
                        val filter = uiState.filter
                        if (filter is HeatmapFilter.Year) {
                            ScrollableWeeksHeatmap(
                                weeks = uiState.weeks,
                                maxCount = uiState.maxCount,
                                onDayClick = onDayClick,
                                labelStart = LocalDate.of(filter.year, 1, 1),
                                labelEnd = minOf(LocalDate.of(filter.year, 12, 31), today),
                                scrollToEnd = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            ScrollableWeeksHeatmap(
                                weeks = uiState.weeks,
                                maxCount = uiState.maxCount,
                                onDayClick = onDayClick,
                                labelStart = today.minusDays(364),
                                labelEnd = today,
                                scrollToEnd = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "热力图统计每个笔记归属的日期：快速笔记按文件名中的时间戳计入创建当天，" +
                        "其余笔记按最后修改时间计入；颜色越深表示当天的笔记越多。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FloatingBackTopBar(
                title = "记录热力图",
                onBackClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                actions = {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = "筛选",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            FilterMenuItem(
                                text = "全部笔记",
                                checked = uiState.filter == HeatmapFilter.All,
                                onClick = {
                                    menuExpanded = false
                                    viewModel.selectFilter(HeatmapFilter.All)
                                }
                            )
                            FilterMenuItem(
                                text = "选择月份",
                                checked = uiState.filter is HeatmapFilter.Month,
                                onClick = {
                                    menuExpanded = false
                                    pickerMode = HeatmapPickerMode.MONTH
                                }
                            )
                            FilterMenuItem(
                                text = "选择年份",
                                checked = uiState.filter is HeatmapFilter.Year,
                                onClick = {
                                    menuExpanded = false
                                    pickerMode = HeatmapPickerMode.YEAR
                                }
                            )
                        }
                    }
                }
            )
        }
    }

    val currentPickerMode = pickerMode
    if (currentPickerMode != null) {
        val filter = uiState.filter
        MonthYearPickerDialog(
            mode = currentPickerMode,
            initialYear = when (filter) {
                is HeatmapFilter.Month -> filter.yearMonth.year
                is HeatmapFilter.Year -> filter.year
                HeatmapFilter.All -> today.year
            },
            yearsWithNotes = uiState.yearsWithNotes,
            monthsWithNotes = uiState.monthsWithNotes,
            onDismiss = { pickerMode = null },
            onYearSelected = { year -> viewModel.selectFilter(HeatmapFilter.Year(year)) },
            onMonthSelected = { yearMonth ->
                viewModel.selectFilter(HeatmapFilter.Month(yearMonth))
            }
        )
    }
}

@Composable
private fun FilterMenuItem(
    text: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        onClick = onClick
    )
}

private fun filterCaption(filter: HeatmapFilter): String = when (filter) {
    HeatmapFilter.All -> "最近一年"
    is HeatmapFilter.Year -> "${filter.year}年"
    is HeatmapFilter.Month ->
        "${filter.yearMonth.year}年${filter.yearMonth.monthValue}月"
}

/**
 * 横向可滑动的周网格热力图（一列一周，周一在首行）
 *
 * 结构与侧边栏一致：标签行和格子行在同一个横向滚动容器里，保证对齐；
 * 月份标签只标注落在 [labelStart]..[labelEnd] 范围内的月份，
 * 避免年视图首尾跨年的周多出租邻年份的"1月"、当前年未来月份悬在空白列上；
 * [scrollToEnd] 为 true 时初始滚动到最右端（今天）
 */
@Composable
private fun ScrollableWeeksHeatmap(
    weeks: List<List<HeatmapDay>>,
    maxCount: Int,
    onDayClick: (LocalDate) -> Unit,
    labelStart: LocalDate,
    labelEnd: LocalDate,
    scrollToEnd: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(weeks, scrollToEnd) {
        if (scrollToEnd && weeks.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = modifier.horizontalScroll(scrollState)) {
        // 月份标签行：某周包含范围内当月 1 号时在该列上方标注
        Row(horizontalArrangement = Arrangement.spacedBy(CELL_SPACING_DP.dp)) {
            weeks.forEach { week ->
                val monthStart = week.firstOrNull {
                    it.date.dayOfMonth == 1 && !it.date.isBefore(labelStart) &&
                        !it.date.isAfter(labelEnd)
                }
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
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(CELL_SPACING_DP.dp)) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(CELL_SPACING_DP.dp)) {
                    week.forEach { day ->
                        WeekGridCell(day = day, maxCount = maxCount, onDayClick = onDayClick)
                    }
                }
            }
        }
    }
}

/**
 * 单月日历视图：一行一周（周一在首列），格子更大并标注日期数字，
 * 颜色按当月最大值分档；有笔记的格子可点击定位到当天笔记
 */
@Composable
private fun MonthCalendarGrid(
    rows: List<List<HeatmapDay?>>,
    maxCount: Int,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 星期表头（周一开头，与周网格口径一致）
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 6.dp)
                )
            }
        }

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MONTH_CELL_SPACING_DP.dp)
            ) {
                row.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(MONTH_CELL_SPACING_DP.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null && !day.isFuture) {
                            val level = NoteHeatmapAggregator.colorLevel(day.count, maxCount)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(monthCellColor(level))
                                    // 0 篇的日子不可点
                                    .then(
                                        if (day.count > 0) {
                                            Modifier.clickable { onDayClick(day.date) }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = monthCellTextColor(level)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekGridCell(
    day: HeatmapDay,
    maxCount: Int,
    onDayClick: (LocalDate) -> Unit
) {
    if (day.isFuture) {
        // 未来/范围外日期占位，保持网格对齐
        Spacer(modifier = Modifier.size(CELL_SIZE_DP.dp))
        return
    }
    Box(
        modifier = Modifier
            .size(CELL_SIZE_DP.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(weekCellColor(day.count, maxCount))
            // 0 篇的日子不可点
            .then(
                if (day.count > 0) {
                    Modifier.clickable { onDayClick(day.date) }
                } else {
                    Modifier
                }
            )
    )
}

@Composable
private fun weekCellColor(count: Int, maxCount: Int): Color {
    val level = NoteHeatmapAggregator.colorLevel(count, maxCount)
    if (level == 0) {
        return MaterialTheme.colorScheme.surfaceVariant
    }
    return MaterialTheme.colorScheme.primary.copy(alpha = LEVEL_ALPHAS[level - 1])
}

@Composable
private fun monthCellColor(level: Int): Color {
    if (level == 0) {
        return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    return MaterialTheme.colorScheme.primary.copy(alpha = LEVEL_ALPHAS[level - 1])
}

@Composable
private fun monthCellTextColor(level: Int): Color {
    return if (level >= LEVEL_ALPHAS.size - 1) {
        // 深底色上用页面底色反白，保证可读
        LocalFiberSurfaceColors.current.pageBackground
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private val LEVEL_ALPHAS = floatArrayOf(0.25f, 0.45f, 0.7f, 1f)
private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private const val CELL_SIZE_DP = 13
private const val CELL_SPACING_DP = 3
private const val MONTH_CELL_SPACING_DP = 2
private const val MONTH_LABEL_HEIGHT_DP = 14
private val WEEKS_HEATMAP_HEIGHT_DP =
    MONTH_LABEL_HEIGHT_DP + 4 + CELL_SIZE_DP * 7 + CELL_SPACING_DP * 6
