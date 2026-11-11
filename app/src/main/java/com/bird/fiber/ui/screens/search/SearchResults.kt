package com.bird.fiber.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.ui.screens.filelist.FileListViewModel
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors
import timber.log.Timber

@Composable
internal fun SearchResultsContent(
    searchQuery: String,
    lazyPagingItems: LazyPagingItems<MarkdownFileMeta>,
    searchSort: FileListViewModel.SearchSort,
    onSortChange: (FileListViewModel.SearchSort) -> Unit,
    onFileClick: (MarkdownFileMeta) -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier
) {
    // 始终保存位置，加载/空结果分支也不能销毁它；NavHost 返回时可直接恢复。
    val listState = rememberLazyListState()
    var savedAnchorUri by rememberSaveable { mutableStateOf<String?>(null) }
    var savedAnchorOffset by rememberSaveable { mutableIntStateOf(0) }
    // 只在重新进入组合后恢复，避免点击结果、尚未完成导航时提前消耗锚点。
    var pendingAnchorUri by remember { mutableStateOf(savedAnchorUri) }
    val refreshState = lazyPagingItems.loadState.refresh
    LaunchedEffect(pendingAnchorUri, lazyPagingItems.itemSnapshotList, refreshState) {
        val uri = pendingAnchorUri ?: return@LaunchedEffect
        val index = lazyPagingItems.itemSnapshotList.items.indexOfFirst { it.uri == uri }
        if (index >= 0) {
            // 跨库切换会让 Room 刷新分页窗口；用笔记 URI 恢复，而不是旧窗口中的下标。
            listState.scrollToItem(index + 1, savedAnchorOffset)
        }
        if (index >= 0 || refreshState is LoadState.NotLoading) {
            // 笔记若已不在结果中，保留可用位置，不把旧锚点带入后续搜索。
            pendingAnchorUri = null
            savedAnchorUri = null
        }
    }
    val hasItems = lazyPagingItems.itemCount > 0

    when {
        refreshState is LoadState.Loading && !hasItems -> {
            SearchResultsSkeleton(topPadding = topPadding, modifier = modifier.fillMaxSize())
        }

        refreshState is LoadState.Error && !hasItems -> {
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

        !hasItems -> {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .padding(top = topPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    SearchFeedbackCard(
                        icon = Icons.Default.Description,
                        title = if (searchQuery.isBlank()) "所选日期没有笔记" else "没有找到相关内容",
                        description = if (searchQuery.isBlank()) "可以调整或清除日期筛选。" else "试试更短的词，或者调整日期筛选。",
                        actionText = "刷新",
                        onActionClick = { lazyPagingItems.refresh() }
                    )
                }
        }
        else -> {
            Box(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 11.dp, end = 11.dp, top = topPadding, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    item(key = "search-summary") {
                        SearchSummaryCard(
                            searchQuery = searchQuery.ifBlank { "按日期查找" },
                            resultCount = lazyPagingItems.itemCount,
                            searchSort = searchSort,
                            onSortChange = onSortChange
                        )
                    }

                    items(
                        count = lazyPagingItems.itemCount,
                        key = lazyPagingItems.itemKey { it.uri }
                    ) { index ->
                        val file = lazyPagingItems[index]
                        if (file != null) {
                            SearchResultItem(
                                file = file,
                                searchQuery = searchQuery,
                                onClick = {
                                    val firstResultIndex = listState.firstVisibleItemIndex - 1
                                    savedAnchorUri = if (firstResultIndex in 0 until lazyPagingItems.itemCount) {
                                        lazyPagingItems.peek(firstResultIndex)?.uri
                                    } else null
                                    savedAnchorOffset = listState.firstVisibleItemScrollOffset
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

                if (refreshState is LoadState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = topPadding + 8.dp, end = 20.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp
                    )
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
            containerColor = LocalFiberSurfaceColors.current.contentCard
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
