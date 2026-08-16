package com.bird.fiber.ui.screens.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig as AndroidPagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.bird.fiber.data.config.PagingConfig
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.toMarkdownFileMeta
import com.bird.fiber.data.model.MarkdownFileMeta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 笔记浏览页 ViewModel（"全部笔记"）
 *
 * 跨库浏览，数据来自笔记索引表（Room PagingSource 在表变化时自动失效刷新）
 */
@HiltViewModel
class NoteListViewModel @Inject constructor(
    markdownFileDao: MarkdownFileDao
) : ViewModel() {

    val pager: Flow<PagingData<MarkdownFileMeta>> = Pager(
        config = AndroidPagingConfig(
            pageSize = PagingConfig.PAGE_SIZE,
            enablePlaceholders = false,
            prefetchDistance = PagingConfig.PREFETCH_DISTANCE,
            initialLoadSize = PagingConfig.INITIAL_LOAD_SIZE
        ),
        pagingSourceFactory = { markdownFileDao.getAllFilesSummary() }
    ).flow.map { pagingData ->
        pagingData.map { summary -> summary.toMarkdownFileMeta() }
    }
}
