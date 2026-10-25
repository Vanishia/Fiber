package com.bird.fiber.ui.screens.heatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.local.library.LibraryNoteCount
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownHeatmapEntry
import com.bird.fiber.domain.heatmap.HeatmapDay
import com.bird.fiber.domain.heatmap.HeatmapEntry
import com.bird.fiber.domain.heatmap.NoteHeatmapAggregator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 热力图页的筛选范围 */
sealed interface HeatmapFilter {
    /** 最近一年（默认） */
    data object All : HeatmapFilter

    /** 某一整年 */
    data class Year(val year: Int) : HeatmapFilter

    /** 某一单月（日历视图） */
    data class Month(val yearMonth: YearMonth) : HeatmapFilter
}

/**
 * 热力图页面 ViewModel
 *
 * 与侧边栏一样直接订阅笔记索引表；
 * 后台计算筛选结果，计算期间保留已有网格，完成后直接展示新结果。
 */
@HiltViewModel
class HeatmapPageViewModel internal constructor(
    markdownFileDao: MarkdownFileDao,
    calculationDispatcher: CoroutineDispatcher
) : ViewModel() {

    @Inject
    constructor(markdownFileDao: MarkdownFileDao) : this(markdownFileDao, Dispatchers.Default)

    private val filter = MutableStateFlow<HeatmapFilter>(HeatmapFilter.All)

    val uiState: StateFlow<HeatmapPageUiState> = combine(
        filter, markdownFileDao.observeHeatmapEntries()
    ) { currentFilter, rows ->
        buildState(currentFilter, rows, LocalDate.now())
    }
        .flowOn(calculationDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HeatmapPageUiState()
        )

    fun selectFilter(newFilter: HeatmapFilter) {
        filter.value = newFilter
    }

    private fun buildState(
        currentFilter: HeatmapFilter,
        rows: List<MarkdownHeatmapEntry>,
        today: LocalDate
    ): HeatmapPageUiState {
        val counts = NoteHeatmapAggregator.countByDate(
            rows.map { HeatmapEntry(it.name, it.lastModified) }
        )
        val statistics = aggregateHeatmapStatistics(rows, currentFilter, today)
        val yearsWithNotes = counts.keys.map { it.year }.distinct().sortedDescending()
        val monthsWithNotes = counts.keys.mapTo(hashSetOf()) { YearMonth.from(it) }

        val base = HeatmapPageUiState(
            filter = currentFilter,
            timeOfDayCounts = statistics.timeOfDayCounts,
            libraryNoteCounts = statistics.libraryNoteCounts,
            noteStats = statistics.noteStats,
            yearsWithNotes = yearsWithNotes,
            monthsWithNotes = monthsWithNotes,
            isLoaded = true
        )
        return when (currentFilter) {
            HeatmapFilter.All -> base.copy(
                weeks = NoteHeatmapAggregator.buildWeeks(
                    counts = counts,
                    today = today,
                    weeks = NoteHeatmapAggregator.recentYearWeekCount(today)
                ),
                maxCount = counts.filterKeys { it in today.minusDays(364)..today }
                    .values.maxOrNull() ?: 0
            )

            is HeatmapFilter.Year -> base.copy(
                weeks = NoteHeatmapAggregator.buildYearWeeks(counts, currentFilter.year, today),
                maxCount = counts.filterKeys { it.year == currentFilter.year }
                    .values.maxOrNull() ?: 0
            )

            is HeatmapFilter.Month -> base.copy(
                monthRows = NoteHeatmapAggregator.buildMonthRows(
                    counts, currentFilter.yearMonth, today
                ),
                // 单月视图按当月最大值分档
                maxCount = counts.filterKeys { YearMonth.from(it) == currentFilter.yearMonth }
                    .values.maxOrNull() ?: 0
            )
        }
    }

}

/** 笔记数量与字数统计（热力图页统计卡片） */
data class NoteStats(
    /** 笔记总数 */
    val totalCount: Int = 0,
    /** 笔记总字数 */
    val totalChars: Long = 0,
    /** 当前视图范围内的笔记数 */
    val periodCount: Int = 0,
    /** 当前视图范围内的字数 */
    val periodChars: Long = 0
)

data class HeatmapPageUiState(
    val filter: HeatmapFilter = HeatmapFilter.All,
    val timeOfDayCounts: List<Int> = List(4) { 0 },
    val libraryNoteCounts: List<LibraryNoteCount> = emptyList(),
    val noteStats: NoteStats = NoteStats(),
    /** 周网格数据（全部笔记 / 整年视图），一列一周 */
    val weeks: List<List<HeatmapDay>> = emptyList(),
    /** 单月日历数据，一行一周，月外日期为 null */
    val monthRows: List<List<HeatmapDay?>> = emptyList(),
    val maxCount: Int = 0,
    /** 有笔记的年份（新→旧），供年份选择 */
    val yearsWithNotes: List<Int> = emptyList(),
    /** 有笔记的月份，供月份面板置灰 */
    val monthsWithNotes: Set<YearMonth> = emptySet(),
    val isLoaded: Boolean = false
)
