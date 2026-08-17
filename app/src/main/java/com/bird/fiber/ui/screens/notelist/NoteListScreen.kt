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
import com.bird.fiber.data.model.MarkdownFileMeta
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
    onSearchClick: () -> Unit,
    onAddLibrary: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageAttachments: (String) -> Unit,
    onAllNotesClick: () -> Unit,
    onHeatmapClick: () -> Unit,
    onHeatmapDayClick: (LocalDate) -> Unit,
    onLibrarySelected: (String) -> Unit,
    viewModel: NoteListViewModel = hiltViewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activeLibraryId by viewModel.activeLibraryId.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val emptyText by viewModel.emptyText.collectAsStateWithLifecycle()

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
        NoteListScreen(
            title = title,
            emptyText = emptyText,
            pager = viewModel.pager,
            onMenuClick = { scope.launch { drawerState.open() } },
            onSearchClick = onSearchClick,
            onFileClick = onFileClick
        )
    }
}

/**
 * 笔记浏览页（"全部笔记" / 某日笔记共用）
 *
 * 复用主界面的卡片与顶栏样式（菜单 + 搜索 + 标题），
 * 但没有快速笔记输入框和新建按钮
 */
@Composable
fun NoteListScreen(
    title: String,
    emptyText: String,
    pager: Flow<PagingData<MarkdownFileMeta>>,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFileClick: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    NoteList(
                        lazyPagingItems = lazyPagingItems,
                        onFileClick = onFileClick,
                        topPadding = listTopPadding
                    )
                }
            }

            FloatingTopAppBar(
                title = title,
                onMenuClick = onMenuClick,
                onSearchClick = onSearchClick,
                onCreateClick = null,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun NoteList(
    lazyPagingItems: LazyPagingItems<MarkdownFileMeta>,
    onFileClick: (String, Boolean) -> Unit,
    topPadding: Dp
) {
    LazyColumn(
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
                    swipeEnabled = false,
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
}
