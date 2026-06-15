package com.bird.fiber.ui.screens.filelist.components

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.bird.fiber.data.model.MarkdownFileMeta
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
    onRenameRequest: (String, String) -> Unit = { _, _ -> },
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    currentLibraryName: String? = null,
    onListScroll: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showLongPressMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
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
                    onListScroll = onListScroll
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
            currentLibraryName = currentLibraryName,
            statusBarTopPadding = statusBarTopPadding,
            modifier = Modifier.align(Alignment.TopCenter)
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
    onListScroll: () -> Unit
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 11.dp, end = 11.dp, top = topPadding, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        items(lazyPagingItems.itemCount) { index ->
            val file = lazyPagingItems[index]
            if (file != null) {
                FileListItem(
                    file = file,
                    displayPreview = file.preview,
                    onClick = { onFileClick(file.uri, false) },
                    onLongClick = { onLongPress(file) },
                    onDelete = { onDeleteRequest(file.name.removeSuffix(".md"), file.uri) },
                    onEdit = { onEditRequest(file.uri) }
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

@Composable
private fun FloatingTopAppBar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCreateClick: () -> Unit,
    currentLibraryName: String?,
    statusBarTopPadding: androidx.compose.ui.unit.Dp,
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMenuClick, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "菜单",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onSearchClick, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = currentLibraryName ?: "Fiber",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onCreateClick, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "新建",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
