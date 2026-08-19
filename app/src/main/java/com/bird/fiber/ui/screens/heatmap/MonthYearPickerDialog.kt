package com.bird.fiber.ui.screens.heatmap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth

/** 面板打开时的用途：选月份（默认带年份切换）或直接选年份 */
enum class HeatmapPickerMode { MONTH, YEAR }

/**
 * 月份/年份选择面板
 *
 * [HeatmapPickerMode.MONTH]：4×3 月份网格（无笔记的月份置灰不可选），
 * 顶部年份可 ±1 切换，点年份进入年份列表，选中年份后回到月份面板继续选月；
 * [HeatmapPickerMode.YEAR]：只有年份列表（上下滚动，只列出有笔记的年份），
 * 点年份直接进入该年的年视图
 */
@Composable
fun MonthYearPickerDialog(
    mode: HeatmapPickerMode,
    initialYear: Int,
    yearsWithNotes: List<Int>,
    monthsWithNotes: Set<YearMonth>,
    onDismiss: () -> Unit,
    onYearSelected: (Int) -> Unit,
    onMonthSelected: (YearMonth) -> Unit
) {
    var displayedYear by remember { mutableIntStateOf(initialYear) }
    var showYearList by remember { mutableStateOf(mode == HeatmapPickerMode.YEAR) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = {
            if (showYearList) {
                Text(text = "选择年份", style = MaterialTheme.typography.titleMedium)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { displayedYear -= 1 }) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowLeft,
                            contentDescription = "上一年"
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showYearList = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${displayedYear}年",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "选择年份",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { displayedYear += 1 }) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = "下一年"
                        )
                    }
                }
            }
        },
        text = {
            if (showYearList) {
                YearList(
                    years = yearsWithNotes,
                    onYearClick = { year ->
                        if (mode == HeatmapPickerMode.YEAR) {
                            // 年份入口：直接看该年热力图
                            onYearSelected(year)
                            onDismiss()
                        } else {
                            // 月份入口：回到该年的月份面板继续选月
                            displayedYear = year
                            showYearList = false
                        }
                    }
                )
            } else {
                MonthGrid(
                    year = displayedYear,
                    monthsWithNotes = monthsWithNotes,
                    onMonthClick = { month ->
                        onMonthSelected(YearMonth.of(displayedYear, month))
                        onDismiss()
                    }
                )
            }
        }
    )
}

/** 4 列 × 3 行的月份网格；无笔记的月份置灰且不可点 */
@Composable
private fun MonthGrid(
    year: Int,
    monthsWithNotes: Set<YearMonth>,
    onMonthClick: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until 3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (1..4).forEach { column ->
                    val month = row * 4 + column
                    val hasNotes = monthsWithNotes.contains(YearMonth.of(year, month))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (hasNotes) {
                                    Modifier.clickable { onMonthClick(month) }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${month}月",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = if (hasNotes) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 有笔记年份的上下滚动列表 */
@Composable
private fun YearList(
    years: List<Int>,
    onYearClick: (Int) -> Unit
) {
    if (years.isEmpty()) {
        Text(
            text = "还没有笔记记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        return
    }
    LazyColumn(
        modifier = Modifier.heightIn(max = YEAR_LIST_MAX_HEIGHT_DP.dp)
    ) {
        items(years.size) { index ->
            val year = years[index]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onYearClick(year) }
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "${year}年",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private const val YEAR_LIST_MAX_HEIGHT_DP = 280
