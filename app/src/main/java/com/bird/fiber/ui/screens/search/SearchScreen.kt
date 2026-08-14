package com.bird.fiber.ui.screens.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.ui.screens.filelist.FileListViewModel
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors

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
    headerModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    viewModel: FileListViewModel = hiltViewModel()
) {
    val searchScope by viewModel.searchScope.collectAsStateWithLifecycle()
    val searchSort by viewModel.searchSort.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    // "随便看看"：当前随机命中的笔记，null 时弹窗不显示
    var randomMemo by remember { mutableStateOf<MarkdownFileMeta?>(null) }

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

    DisposableEffect(Unit) {
        onDispose {
            viewModel.updateSearchQuery("")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LocalFiberSurfaceColors.current.pageBackground,
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
                    onRandomMemoClick = {
                        viewModel.loadRandomMemo { memo -> randomMemo = memo }
                    },
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
                modifier = headerModifier.align(Alignment.TopCenter)
            )

            // "随便看看"预览弹窗：点遮罩收起，复用搜索结果的跨库打开逻辑
            RandomMemoSheet(
                memo = randomMemo,
                onDismiss = { randomMemo = null },
                onNext = {
                    viewModel.loadRandomMemo { memo -> randomMemo = memo }
                },
                onOpen = { memo ->
                    randomMemo = null
                    viewModel.openSearchResult(memo, onFileClick)
                }
            )
        }
    }
}
