package com.bird.fiber.ui.screens.filelist.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.ui.components.FloatingTopAppBar
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors
import timber.log.Timber

@Composable
fun DeleteConfirmDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("确定要删除文件「$fileName」吗？此操作无法撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListContent(
    lazyPagingItems: LazyPagingItems<MarkdownFileMeta>,
    onFileClick: (String, Boolean) -> Unit = { _, _ -> },
    searchQuery: String,
    onDeleteRequest: (String, String) -> Unit = { _, _ -> },
    onEditRequest: (String) -> Unit = {},
    onCopyRequest: (String, String) -> Unit = { _, _ -> },
    onShareRequest: (String, String) -> Unit = { _, _ -> },
    onRenameRequest: (String, String) -> Unit = { _, _ -> },
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    currentLibraryName: String? = null,
    onListScroll: () -> Unit = {},
    scrollToTopSignal: Int = 0,
    topBarModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    var showLongPressMenu by remember { mutableStateOf(false) }
    // 菜单条目较多，跳过半展开态直接完整展开，避免底部按钮被遮挡
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFile by remember { mutableStateOf<MarkdownFileMeta?>(null) }
    val density = LocalDensity.current
    val statusBarTopPadding = with(density) {
        WindowInsets.statusBars.getTop(density).toDp()
    }
    val listTopPadding = statusBarTopPadding + 56.dp

    val isInitialRefreshLoading =
        lazyPagingItems.itemCount == 0 &&
            lazyPagingItems.loadState.refresh is androidx.paging.LoadState.Loading
    val hasRefreshError = lazyPagingItems.loadState.refresh is androidx.paging.LoadState.Error
    val isBackgroundRefreshing =
        lazyPagingItems.itemCount > 0 &&
            lazyPagingItems.loadState.refresh is androidx.paging.LoadState.Loading

    Box(modifier = modifier.fillMaxSize()) {
        when {
            hasRefreshError -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "加载失败，请重试",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { lazyPagingItems.refresh() }) {
                            Text("重试")
                        }
                    }
                }
            }

            isInitialRefreshLoading -> {
                FileListSkeleton(
                    topPadding = listTopPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                FileList(
                    lazyPagingItems = lazyPagingItems,
                    onFileClick = onFileClick,
                    searchQuery = searchQuery,
                    onDeleteRequest = onDeleteRequest,
                    onEditRequest = onEditRequest,
                    onLongPress = { file ->
                        Timber.d("长按文件项，显示菜单: ${file.name}")
                        selectedFile = file
                        showLongPressMenu = true
                    },
                    topPadding = listTopPadding,
                    onListScroll = onListScroll,
                    scrollToTopSignal = scrollToTopSignal
                )
            }
        }

        if (isBackgroundRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusBarTopPadding + 72.dp, end = 16.dp)
                    .size(20.dp),
                strokeWidth = 2.dp
            )
        }

        FloatingTopAppBar(
            onMenuClick = onMenuClick,
            onSearchClick = onSearchClick,
            onCreateClick = onCreateClick,
            title = currentLibraryName ?: "Fiber",
            modifier = topBarModifier.align(Alignment.TopCenter)
        )
    }

    LongPressMenuBottomSheet(
        isVisible = showLongPressMenu,
        onDismiss = { showLongPressMenu = false },
        onEdit = {
            selectedFile?.let { onEditRequest(it.uri) }
        },
        onDelete = {
            selectedFile?.let { onDeleteRequest(it.name.removeSuffix(".md"), it.uri) }
        },
        onCopy = {
            selectedFile?.let { onCopyRequest(it.name.removeSuffix(".md"), it.uri) }
        },
        onShare = {
            selectedFile?.let { onShareRequest(it.name.removeSuffix(".md"), it.uri) }
        },
        onRename = {
            selectedFile?.let { onRenameRequest(it.name.removeSuffix(".md"), it.uri) }
        },
        sheetState = sheetState
    )
}

@Composable
fun FileListSkeleton(
    topPadding: androidx.compose.ui.unit.Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalFiberSurfaceColors.current.pageBackground.luminance() < 0.5f

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 11.dp, end = 11.dp, top = topPadding, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        items(6) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = LocalFiberSurfaceColors.current.contentCard
                ),
                border = if (isDarkTheme) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                } else {
                    null
                },
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isDarkTheme) 0.dp else 2.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 9.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(18.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    ) {}

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp),
                        shape = RoundedCornerShape(7.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {}

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(14.dp),
                        shape = RoundedCornerShape(7.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {}

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.28f)
                            .height(12.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun FileList(
    lazyPagingItems: LazyPagingItems<MarkdownFileMeta>,
    onFileClick: (String, Boolean) -> Unit,
    searchQuery: String,
    onDeleteRequest: (String, String) -> Unit,
    onEditRequest: (String) -> Unit,
    onLongPress: (MarkdownFileMeta) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    onListScroll: () -> Unit,
    scrollToTopSignal: Int = 0
) {
    val listState = rememberLazyListState()
    var lastScrollOffset by remember { mutableStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemScrollOffset }
            .collect { offset ->
                if (offset != lastScrollOffset && offset > 10) {
                    onListScroll()
                }
                lastScrollOffset = offset
            }
    }

    // 新笔记创建后滚动回顶部，让新卡片的插入动画出现在可视区域内
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 11.dp, end = 11.dp, top = topPadding, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        items(
            count = lazyPagingItems.itemCount,
            key = lazyPagingItems.itemKey { it.uri }
        ) { index ->
            val file = lazyPagingItems[index]
            if (file != null) {
                FileListItem(
                    file = file,
                    displayPreview = file.preview,
                    onClick = { onFileClick(file.uri, false) },
                    onLongClick = { onLongPress(file) },
                    onDelete = { onDeleteRequest(file.name.removeSuffix(".md"), file.uri) },
                    onEdit = { onEditRequest(file.uri) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        when (lazyPagingItems.loadState.append) {
            is androidx.paging.LoadState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            is androidx.paging.LoadState.Error -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "加载更多失败",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    if (
        lazyPagingItems.itemCount == 0 &&
        lazyPagingItems.loadState.refresh is androidx.paging.LoadState.NotLoading
    ) {
        FileListEmpty(
            searchQuery = searchQuery,
            modifier = Modifier.fillMaxSize()
        )
    }
}
