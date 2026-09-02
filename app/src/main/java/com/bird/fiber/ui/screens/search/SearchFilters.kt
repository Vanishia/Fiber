package com.bird.fiber.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bird.fiber.ui.screens.filelist.FileListViewModel
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

@Composable
internal fun EmptySearchContent(
    onQuickSearchClick: (String) -> Unit,
    scope: FileListViewModel.SearchScope,
    onScopeChange: (FileListViewModel.SearchScope) -> Unit,
    dateRange: FileListViewModel.SearchDateRange,
    onDateRangeChange: (FileListViewModel.SearchDateRange) -> Unit,
    quickSearchKeywords: List<String>,
    onSaveQuickSearchKeywords: (List<String>) -> Unit,
    onRandomMemoClick: () -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier
) {
    var showKeywordEditor by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = LocalFiberSurfaceColors.current.contentCard
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "搜索笔记",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "同时匹配标题、正文和路径。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SearchOptionSection(
                title = "搜索范围",
                options = listOf("当前库" to FileListViewModel.SearchScope.CURRENT_LIBRARY, "全库" to FileListViewModel.SearchScope.ALL_LIBRARIES),
                selected = scope,
                onSelected = onScopeChange
            )
        }

        item {
            SearchQuickSection(
                title = "快捷入口",
                subtitle = "用常搜词直接起步",
                queries = quickSearchKeywords,
                onQuickSearchClick = onQuickSearchClick,
                isRecent = false,
                trailing = {
                    IconButton(onClick = { showKeywordEditor = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑快捷关键词",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }

        item {
            DateFilterSection(
                dateRange = dateRange,
                onDateRangeChange = onDateRangeChange
            )
        }

        item {
            RandomMemoEntryCard(onClick = onRandomMemoClick)
        }
    }

    if (showKeywordEditor) {
        QuickSearchKeywordEditorDialog(
            initialKeywords = quickSearchKeywords,
            onDismiss = { showKeywordEditor = false },
            onSave = { keywords ->
                onSaveQuickSearchKeywords(keywords)
                showKeywordEditor = false
            }
        )
    }
}

/**
 * 快捷入口关键词编辑弹窗
 *
 * 多行输入，每行一个关键词，也可用逗号/空格分隔；
 * 清空后保存会恢复默认关键词
 */
@Composable
private fun QuickSearchKeywordEditorDialog(
    initialKeywords: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var text by remember(initialKeywords) { mutableStateOf(initialKeywords.joinToString("\n")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑快捷关键词") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("每行一个关键词") },
                    minLines = 3,
                    maxLines = 6
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "支持换行、逗号或空格分隔；留空保存将恢复默认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(parseKeywords(text)) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 把用户输入拆成关键词列表：去空白、去重，最多保留 12 个 */
private fun parseKeywords(text: String): List<String> =
    text.split('\n', ',', '，', '、', ' ', '　')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(12)

/**
 * "条件搜索"卡片——按笔记修改日期过滤搜索结果
 *
 * 预设区间（最近 7/30/90 天）以点击时刻为基准；
 * "自定义"通过日期区间选择器指定起止日期
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DateFilterSection(
    dateRange: FileListViewModel.SearchDateRange,
    onDateRangeChange: (FileListViewModel.SearchDateRange) -> Unit
) {
    var showRangePicker by remember { mutableStateOf(false) }
    val presets = listOf(
        FileListViewModel.SearchDatePreset.ALL_TIME to "全部时间",
        FileListViewModel.SearchDatePreset.LAST_7_DAYS to "最近7天",
        FileListViewModel.SearchDatePreset.LAST_30_DAYS to "最近30天",
        FileListViewModel.SearchDatePreset.LAST_90_DAYS to "最近90天"
    )

    SearchSectionCard(title = "条件搜索", subtitle = "按修改日期过滤结果，可与关键词叠加") {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (preset, label) ->
                FilterChip(
                    selected = dateRange.preset == preset,
                    onClick = {
                        onDateRangeChange(
                            FileListViewModel.SearchDateRange(
                                preset = preset,
                                fromMillis = presetFromMillis(preset),
                                toMillis = null
                            )
                        )
                    },
                    label = { Text(label) }
                )
            }
            FilterChip(
                selected = dateRange.preset == FileListViewModel.SearchDatePreset.CUSTOM,
                onClick = { showRangePicker = true },
                label = {
                    val isCustomActive = dateRange.preset == FileListViewModel.SearchDatePreset.CUSTOM &&
                        dateRange.fromMillis != null && dateRange.toMillis != null
                    Text(
                        if (isCustomActive) {
                            "自定义（${formatShortDate(dateRange.fromMillis!!)} ~ ${formatShortDate(dateRange.toMillis!!)}）"
                        } else {
                            "自定义"
                        }
                    )
                }
            )
        }
    }

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = dateRange.fromMillis,
            initialSelectedEndDateMillis = dateRange.toMillis
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null &&
                        pickerState.selectedEndDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            onDateRangeChange(
                                FileListViewModel.SearchDateRange(
                                    preset = FileListViewModel.SearchDatePreset.CUSTOM,
                                    fromMillis = start,
                                    toMillis = end + DAY_MILLIS - 1
                                )
                            )
                        }
                        showRangePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text("取消") }
            }
        ) {
            DateRangePicker(state = pickerState)
        }
    }
}

/** 预设区间的起点毫秒值；null 表示不限时间 */
private fun presetFromMillis(preset: FileListViewModel.SearchDatePreset): Long? = when (preset) {
    FileListViewModel.SearchDatePreset.ALL_TIME -> null
    FileListViewModel.SearchDatePreset.LAST_7_DAYS -> System.currentTimeMillis() - 7 * DAY_MILLIS
    FileListViewModel.SearchDatePreset.LAST_30_DAYS -> System.currentTimeMillis() - 30 * DAY_MILLIS
    FileListViewModel.SearchDatePreset.LAST_90_DAYS -> System.currentTimeMillis() - 90 * DAY_MILLIS
    FileListViewModel.SearchDatePreset.CUSTOM -> null
}

private fun formatShortDate(millis: Long): String =
    SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(millis))

/**
 * "随机漫步"入口卡片——整卡可点击
 *
 * 不需要关键词，从全库随机捞起一条记忆，
 * 让几万条碎片偶尔重新浮现
 */
@Composable
private fun RandomMemoEntryCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = LocalFiberSurfaceColors.current.contentCard
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "随机漫步",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "按上方搜索范围随机翻出一条旧碎片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun <T> SearchOptionSection(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = LocalFiberSurfaceColors.current.contentCard
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (label, value) ->
                    SegmentedButton(
                        selected = selected == value,
                        onClick = { onSelected(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SearchQuickSection(
    title: String,
    subtitle: String,
    queries: List<String>,
    onQuickSearchClick: (String) -> Unit,
    isRecent: Boolean,
    trailing: (@Composable () -> Unit)? = null
) {
    SearchSectionCard(title = title, subtitle = subtitle, trailing = trailing) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            queries.forEach { query ->
                SuggestionChip(
                    onClick = { onQuickSearchClick(query) },
                    label = { Text(query) },
                    icon = {
                        Icon(
                            imageVector = if (isRecent) Icons.Default.AccessTime else Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isRecent) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        labelColor = if (isRecent) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(
                            alpha = if (isRecent) 0.14f else 0.2f
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun SearchSectionCard(
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = LocalFiberSurfaceColors.current.contentCard
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                trailing?.invoke()
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
