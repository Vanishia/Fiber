package com.bird.fiber.ui.screens.notelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bird.fiber.data.config.PagingConfig
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.ui.components.DraggableScrollbar
import com.bird.fiber.ui.components.FloatingTopAppBar
import com.bird.fiber.ui.screens.filelist.components.FileListItem
import com.bird.fiber.ui.screens.filelist.components.FileListSkeleton
import com.bird.fiber.ui.screens.sidebar.SidebarContent
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 笔记浏览页路由入口（"全部笔记" / "当日笔记"）
 *
 * 模式由导航参数决定，见 [NoteListViewModel]
 */
@Composable
fun NoteListRouteScreen(
    onBackClick: () -> Unit,
    onFileClick: (String, Boolean) -> Unit,
    onSearchClick: (LocalDate?) -> Unit,
    onAddLibrary: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageAttachments: (String) -> Unit,
    onAllNotesClick: () -> Unit,
    onHeatmapClick: () -> Unit,
    onHeatmapDayClick: (LocalDate) -> Unit,
    onLibrarySelected: (String) -> Unit,
    viewModel: NoteListViewModel = hiltViewModel()
) {
    val date by viewModel.date.collectAsStateWithLifecycle()

    if (date != null) {
        NoteListWithActions(
            viewModel = viewModel,
            onMenuClick = {},
            onSearchClick = { onSearchClick(date) },
            onFileClick = onFileClick,
            onAddLibrary = onAddLibrary,
            date = date,
            onBackClick = onBackClick
        )
        return
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activeLibraryId by viewModel.activeLibraryId.collectAsStateWithLifecycle()

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                SidebarContent(
                    selectedLibraryId = activeLibraryId,
                    onLibrarySelected = { libraryId ->
                        closeDrawer()
                        onLibrarySelected(libraryId)
                    },
                    onAddLibrary = {
                        closeDrawer()
                        onAddLibrary()
                    },
                    onSettingsClick = {
                        closeDrawer()
                        onSettingsClick()
                    },
                    onManageAttachments = { libraryId ->
                        closeDrawer()
                        onManageAttachments(libraryId)
                    },
                    onAllNotesClick = {
                        closeDrawer()
                        onAllNotesClick()
                    },
                    onHeatmapClick = {
                        closeDrawer()
                        onHeatmapClick()
                    },
                    onHeatmapDayClick = { date ->
                        closeDrawer()
                        onHeatmapDayClick(date)
                    }
                )
            }
        }
    ) {
        NoteListWithActions(
            viewModel = viewModel,
            onMenuClick = { scope.launch { drawerState.open() } },
            onSearchClick = { onSearchClick(date) },
            onFileClick = onFileClick,
            onAddLibrary = onAddLibrary
        )
    }
}

/**
 * 笔记浏览页（"全部笔记" / 某日笔记共用）
 *
 * 每日笔记使用独立顶栏：返回、搜索、居中日期以及前后日切换。
 * 全部笔记保留菜单入口；两种模式共用笔记卡片。
 */
@Composable
fun NoteListScreen(
    title: String,
    emptyText: String,
    pager: Flow<PagingData<MarkdownFileMeta>>,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFileClick: (MarkdownFileMeta, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    date: LocalDate? = null,
    onBackClick: () -> Unit = {},
    onDayChange: (LocalDate) -> Unit = {},
    onCreateClick: (() -> Unit)? = null,
    onLongPress: (MarkdownFileMeta) -> Unit = {},
    onDeleteRequest: (MarkdownFileMeta) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {}
) {
    val lazyPagingItems = pager.collectAsLazyPagingItems()

    val density = LocalDensity.current
    val statusBarTopPadding = with(density) {
        WindowInsets.statusBars.getTop(density).toDp()
    }
    val listTopPadding = statusBarTopPadding + 56.dp

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LocalFiberSurfaceColors.current.pageBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = bottomBar,
        snackbarHost = snackbarHost
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                lazyPagingItems.itemCount == 0 &&
                    lazyPagingItems.loadState.refresh is LoadState.Loading -> {
                    FileListSkeleton(
                        topPadding = listTopPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                lazyPagingItems.itemCount == 0 &&
                    lazyPagingItems.loadState.refresh is LoadState.NotLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    key(date) {
                        NoteList(
                            lazyPagingItems = lazyPagingItems,
                            onFileClick = onFileClick,
                            onLongPress = onLongPress,
                            onDeleteRequest = onDeleteRequest,
                            topPadding = listTopPadding
                        )
                    }
                }
            }

            if (date != null) {
                DayNotesTopBar(
                    date = date,
                    onBackClick = onBackClick,
                    onSearchClick = onSearchClick,
                    onPreviousDayClick = { onDayChange(date.minusDays(1)) },
                    onNextDayClick = { onDayChange(date.plusDays(1)) },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            } else {
                FloatingTopAppBar(
                    title = title,
                    onMenuClick = onMenuClick,
                    onSearchClick = onSearchClick,
                    onCreateClick = onCreateClick,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun NoteList(
    lazyPagingItems: LazyPagingItems<MarkdownFileMeta>,
    onFileClick: (MarkdownFileMeta, Boolean) -> Unit,
    onLongPress: (MarkdownFileMeta) -> Unit,
    onDeleteRequest: (MarkdownFileMeta) -> Unit,
    topPadding: Dp
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
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
                        onClick = { onFileClick(file, false) },
                        onLongClick = { onLongPress(file) },
                        onDelete = { onDeleteRequest(file) },
                        onEdit = { onFileClick(file, true) },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            if (lazyPagingItems.loadState.append is LoadState.Loading) {
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
        }

        DraggableScrollbar(
            state = listState,
            // 分页未加载完时预留一页虚拟余量压缩 thumb 行程，缓解追加加载时的位置回跳
            extraUnloadedItems = if (lazyPagingItems.loadState.append.endOfPaginationReached) {
                0
            } else {
                PagingConfig.PAGE_SIZE
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = topPadding + 2.dp, bottom = 10.dp)
        )
    }
}
