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
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownFileSummary
import com.bird.fiber.data.local.library.toMarkdownFileMeta
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.utils.ANY_QUICK_NOTE_GLOB
import com.bird.fiber.utils.quickNoteGlobForDate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 笔记浏览页 ViewModel（"全部笔记" / 某日笔记）
 *
 * 跨库浏览，数据来自笔记索引表（Room PagingSource 在表变化时自动失效刷新）。
 * 导航参数带 date（ISO 格式，如 2026-07-26）时为"当日笔记"模式，
 * 否则为"全部笔记"模式
 *
 * date 必须响应式读取：当日页 → 当日页的导航（launchSingleTop 同路由）
 * 会复用 back stack entry，构造时缓存的 date 不会更新
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val markdownFileDao: MarkdownFileDao,
    libraryRepository: LibraryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val dateFlow: StateFlow<LocalDate?> = savedStateHandle
        .getStateFlow<String?>(ARG_DATE, savedStateHandle.get<String>(ARG_DATE))
        .map { raw -> raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = savedStateHandle.get<String>(ARG_DATE)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        )

    val title: StateFlow<String> = dateFlow
        .map { date -> date?.let { "${it}的笔记" } ?: "全部笔记" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialTitle())

    val emptyText: StateFlow<String> = dateFlow
        .map { date -> if (date != null) "这一天没有笔记" else "还没有笔记" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialEmptyText())

    /** 当前激活库（侧边栏高亮用） */
    val activeLibraryId: StateFlow<String?> = libraryRepository.getActiveLibrary()
        .map { it?.id }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val pager: Flow<PagingData<MarkdownFileMeta>> = dateFlow
        .flatMapLatest { date ->
            Pager(
                config = AndroidPagingConfig(
                    pageSize = PagingConfig.PAGE_SIZE,
                    enablePlaceholders = false,
                    prefetchDistance = PagingConfig.PREFETCH_DISTANCE,
                    initialLoadSize = PagingConfig.INITIAL_LOAD_SIZE
                ),
                pagingSourceFactory = { createPagingSource(date) }
            ).flow.map { pagingData ->
                pagingData.map { summary -> summary.toMarkdownFileMeta() }
            }
        }

    private fun initialTitle(): String =
        dateFlow.value?.let { "${it}的笔记" } ?: "全部笔记"

    private fun initialEmptyText(): String =
        if (dateFlow.value != null) "这一天没有笔记" else "还没有笔记"

    private fun createPagingSource(date: LocalDate?): PagingSource<Int, MarkdownFileSummary> {
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
