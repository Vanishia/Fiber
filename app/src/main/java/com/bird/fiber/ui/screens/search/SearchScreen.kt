package com.bird.fiber.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.ui.screens.filelist.FileListViewModel
import com.bird.fiber.utils.FileUtils
import timber.log.Timber

/**
 * 搜索页面
 *
 * 支持文件名和正文搜索，点击结果直接跳转到编辑器
 * 未来可扩展：相关性排序、日期筛选、标签筛选
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onFileClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FileListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchScope by viewModel.searchScope.collectAsStateWithLifecycle()
    val searchSort by viewModel.searchSort.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val lazyPagingItems = viewModel.pager.collectAsLazyPagingItems()
    val density = LocalDensity.current
    val statusBarTopPadding = with(density) {
        WindowInsets.statusBars.getTop(density).toDp()
    }
    val contentTopPadding = statusBarTopPadding + 56.dp

    LaunchedEffect(searchQuery) {
        viewModel.updateSearchQuery(searchQuery)
    }

    LaunchedEffect(Unit) {
        viewModel.updateSearchScope(FileListViewModel.SearchScope.ALL_LIBRARIES)
        viewModel.updateSearchSort(FileListViewModel.SearchSort.RELEVANCE)
    }

    LaunchedEffect(uiState.searchQuery) {
        lazyPagingItems.refresh()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.updateSearchQuery("")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (searchQuery.isBlank()) {
                EmptySearchContent(
                    onQuickSearchClick = { query -> searchQuery = query },
                    scope = searchScope,
                    onScopeChange = viewModel::updateSearchScope,
                    topPadding = contentTopPadding,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SearchResultsContent(
                    searchQuery = searchQuery,
                    lazyPagingItems = lazyPagingItems,
                    searchSort = searchSort,
                    onSortChange = viewModel::updateSearchSort,
                    onFileClick = { file -> viewModel.openSearchResult(file, onFileClick) },
                    topPadding = contentTopPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            SearchHeader(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onClearClick = { searchQuery = "" },
                onBackClick = onBackClick,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun SearchHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemInDarkTheme()) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(20.dp)
                    )

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "搜标题、正文或路径",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = onClearClick, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchContent(
    onQuickSearchClick: (String) -> Unit,
    scope: FileListViewModel.SearchScope,
    onScopeChange: (FileListViewModel.SearchScope) -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier
) {
    val quickSearches = listOf("日报", "TODO", "会议", "灵感", "项目")
    val recentSearches = listOf("日志", "项目", "想法", "会议", "记录")

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSystemInDarkTheme()) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    }
                ),
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
                queries = quickSearches,
                onQuickSearchClick = onQuickSearchClick,
                isRecent = false
            )
        }

        item {
            SearchQuickSection(
                title = "最近搜索",
                subtitle = "先用静态占位，后面可以接真实历史",
                queries = recentSearches,
                onQuickSearchClick = onQuickSearchClick,
                isRecent = true
            )
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (label, value) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(label) }
                )
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
    isRecent: Boolean
) {
    SearchSectionCard(title = title, subtitle = subtitle) {
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
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SearchResultsContent(
    searchQuery: String,
    lazyPagingItems: LazyPagingItems<MarkdownFileMeta>,
    searchSort: FileListViewModel.SearchSort,
    onSortChange: (FileListViewModel.SearchSort) -> Unit,
    onFileClick: (MarkdownFileMeta) -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier
) {
    val refreshState = lazyPagingItems.loadState.refresh

    when (refreshState) {
        is LoadState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is LoadState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(top = topPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                SearchFeedbackCard(
                    icon = Icons.Default.Search,
                    title = "搜索结果加载失败",
                    description = "可以再试一次，或者换个关键词。",
                    actionText = "重试",
                    onActionClick = { lazyPagingItems.refresh() },
                    isError = true
                )
            }
        }

        else -> {
            if (lazyPagingItems.itemCount == 0) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .padding(top = topPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    SearchFeedbackCard(
                        icon = Icons.Default.Description,
                        title = "没有找到相关内容",
                        description = "试试更短的词，或者换一个描述方式。",
                        actionText = "刷新",
                        onActionClick = { lazyPagingItems.refresh() }
                    )
                }
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 11.dp, end = 11.dp, top = topPadding, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    item {
                        SearchSummaryCard(
                            searchQuery = searchQuery,
                            resultCount = lazyPagingItems.itemCount,
                            searchSort = searchSort,
                            onSortChange = onSortChange
                        )
                    }

                    items(lazyPagingItems.itemCount) { index ->
                        val file = lazyPagingItems[index]
                        if (file != null) {
                            SearchResultItem(
                                file = file,
                                onClick = {
                                    Timber.d("搜索页面点击文件: ${file.name}, uri: ${file.uri}")
                                    onFileClick(file)
                                }
                            )
                        }
                    }

                    if (lazyPagingItems.loadState.append is LoadState.Loading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSummaryCard(
    searchQuery: String,
    resultCount: Int,
    searchSort: FileListViewModel.SearchSort,
    onSortChange: (FileListViewModel.SearchSort) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = searchQuery,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "命中 $resultCount 条结果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "排序",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("相关性") },
                        onClick = {
                            onSortChange(FileListViewModel.SearchSort.RELEVANCE)
                            sortMenuExpanded = false
                        },
                        trailingIcon = {
                            if (searchSort == FileListViewModel.SearchSort.RELEVANCE) Text("✓")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("最近修改") },
                        onClick = {
                            onSortChange(FileListViewModel.SearchSort.RECENT_MODIFIED)
                            sortMenuExpanded = false
                        },
                        trailingIcon = {
                            if (searchSort == FileListViewModel.SearchSort.RECENT_MODIFIED) Text("✓")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFeedbackCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionText: String,
    onActionClick: () -> Unit,
    isError: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isError) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .padding(14.dp)
                        .size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            SuggestionChip(
                onClick = onActionClick,
                label = { Text(actionText) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    labelColor = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    file: MarkdownFileMeta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 13.dp, end = 13.dp, top = 11.dp, bottom = 10.dp)
        ) {
            Text(
                text = file.name.removeSuffix(".md"),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary
            )

            if (file.libraryName.isNotBlank()) {
                Text(
                    text = file.libraryName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (file.preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = file.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FileUtils.formatDate(file.lastModified),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (file.path.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = file.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
