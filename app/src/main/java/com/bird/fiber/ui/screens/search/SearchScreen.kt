package com.bird.fiber.ui.screens.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import com.bird.fiber.ui.screens.filelist.FileListViewModel
import com.bird.fiber.ui.screens.filelist.RandomMemo
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors

/**
 * 搜索页面
 *
 * 支持文件名和正文搜索，点击结果直接跳转到编辑器
 * 支持按修改日期范围过滤结果
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
    val searchQuery by viewModel.searchQueryInput.collectAsStateWithLifecycle()
    val searchScope by viewModel.searchScope.collectAsStateWithLifecycle()
    val searchSort by viewModel.searchSort.collectAsStateWithLifecycle()
    val searchDateRange by viewModel.searchDateRange.collectAsStateWithLifecycle()
    val quickSearchKeywords by viewModel.quickSearchKeywords.collectAsStateWithLifecycle()
    // "随机漫步"：当前随机命中的笔记（含全文），null 时弹窗不显示
    var randomMemo by remember { mutableStateOf<RandomMemo?>(null) }

    val lazyPagingItems = viewModel.pager.collectAsLazyPagingItems()
    val density = LocalDensity.current
    val statusBarTopPadding = with(density) {
        WindowInsets.statusBars.getTop(density).toDp()
    }
    val contentTopPadding = statusBarTopPadding + 56.dp
    val resultsTopPadding = contentTopPadding + if (searchDateRange.isActive) 48.dp else 0.dp

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
            if (searchQuery.isBlank() && !searchDateRange.isActive) {
                EmptySearchContent(
                    onQuickSearchClick = viewModel::updateSearchQuery,
                    scope = searchScope,
                    onScopeChange = viewModel::updateSearchScope,
                    dateRange = searchDateRange,
                    onDateRangeChange = viewModel::updateSearchDateRange,
                    quickSearchKeywords = quickSearchKeywords,
                    onSaveQuickSearchKeywords = viewModel::saveQuickSearchKeywords,
                    onRandomMemoClick = {
                        viewModel.loadRandomMemo(searchScope) { memo -> randomMemo = memo }
                    },
                    topPadding = resultsTopPadding,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SearchResultsContent(
                    searchQuery = searchQuery,
                    lazyPagingItems = lazyPagingItems,
                    searchSort = searchSort,
                    onSortChange = viewModel::updateSearchSort,
                    onFileClick = { file -> viewModel.openSearchResult(file, onFileClick) },
                    topPadding = resultsTopPadding,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (searchDateRange.isActive) {
                SearchDateFilterBar(
                    dateRange = searchDateRange,
                    onClear = { viewModel.updateSearchDateRange(FileListViewModel.SearchDateRange()) },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = contentTopPadding)
                )
            }

            SearchHeader(
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onClearClick = { viewModel.updateSearchQuery("") },
                onBackClick = onBackClick,
                modifier = headerModifier.align(Alignment.TopCenter)
            )

            // "随机漫步"预览弹窗：点遮罩收起，复用搜索结果的跨库打开逻辑
            RandomMemoSheet(
                memo = randomMemo,
                onDismiss = { randomMemo = null },
                onNext = {
                    viewModel.loadRandomMemo(searchScope) { memo -> randomMemo = memo }
                },
                onOpen = { memo ->
                    randomMemo = null
                    viewModel.openSearchResult(memo, onFileClick)
                }
            )
        }
    }
}
