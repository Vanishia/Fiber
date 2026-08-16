package com.bird.fiber.ui.screens.notelist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig as AndroidPagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import com.bird.fiber.data.config.PagingConfig
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownFileSummary
import com.bird.fiber.data.local.library.toMarkdownFileMeta
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.utils.ANY_QUICK_NOTE_GLOB
import com.bird.fiber.utils.quickNoteGlobForDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 笔记浏览页 ViewModel（"全部笔记" / 某日笔记）
 *
 * 跨库浏览，数据来自笔记索引表（Room PagingSource 在表变化时自动失效刷新）。
 * 导航参数带 date（ISO 格式，如 2026-07-26）时为"当日笔记"模式，
 * 否则为"全部笔记"模式
 */
@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val markdownFileDao: MarkdownFileDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val date: LocalDate? = savedStateHandle.get<String>(ARG_DATE)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    val title: String = date?.let { "${it}的笔记" } ?: "全部笔记"
    val emptyText: String = if (date != null) "这一天没有笔记" else "还没有笔记"

    val pager: Flow<PagingData<MarkdownFileMeta>> = Pager(
        config = AndroidPagingConfig(
            pageSize = PagingConfig.PAGE_SIZE,
            enablePlaceholders = false,
            prefetchDistance = PagingConfig.PREFETCH_DISTANCE,
            initialLoadSize = PagingConfig.INITIAL_LOAD_SIZE
        ),
        pagingSourceFactory = { createPagingSource() }
    ).flow.map { pagingData ->
        pagingData.map { summary -> summary.toMarkdownFileMeta() }
    }

    private fun createPagingSource(): PagingSource<Int, MarkdownFileSummary> {
        val day = date ?: return markdownFileDao.getAllFilesSummary()
        val zone = ZoneId.systemDefault()
        return markdownFileDao.getFilesByDaySummary(
            quickNoteGlob = quickNoteGlobForDate(day),
            anyQuickNoteGlob = ANY_QUICK_NOTE_GLOB,
            dayStartMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
            dayEndMillis = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }

    companion object {
        const val ARG_DATE = "date"
    }
}
