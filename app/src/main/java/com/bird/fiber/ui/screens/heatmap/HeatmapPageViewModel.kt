package com.bird.fiber.ui.screens.heatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.domain.heatmap.HeatmapDay
import com.bird.fiber.domain.heatmap.HeatmapEntry
import com.bird.fiber.domain.heatmap.NoteHeatmapAggregator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
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
 * 筛选切换时先短暂展示加载圈再出图，避免布局突变过于生硬
 */
@HiltViewModel
class HeatmapPageViewModel @Inject constructor(
    markdownFileDao: MarkdownFileDao
) : ViewModel() {

    private val countsFlow = markdownFileDao.observeHeatmapEntries()
        .map { rows ->
            NoteHeatmapAggregator.countByDate(
                rows.map { HeatmapEntry(it.name, it.lastModified) }
            )
        }
        .flowOn(Dispatchers.Default)

    private val filter = MutableStateFlow<HeatmapFilter>(HeatmapFilter.All)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HeatmapPageUiState> = combine(filter, countsFlow, ::Pair)
        .transformLatest { (currentFilter, counts) ->
            val state = buildState(currentFilter, counts, LocalDate.now())
            if (!hasEmitted) {
                hasEmitted = true
                emit(state)
            } else {
                // 切换筛选：先清空网格显示加载圈，再出结果
                emit(state.copy(weeks = emptyList(), monthRows = emptyList(), isSwitching = true))
                delay(SWITCH_INDICATOR_MS)
                emit(state)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HeatmapPageUiState()
        )

    private var hasEmitted = false

    fun selectFilter(newFilter: HeatmapFilter) {
        filter.value = newFilter
    }

    private fun buildState(
        currentFilter: HeatmapFilter,
        counts: Map<LocalDate, Int>,
        today: LocalDate
    ): HeatmapPageUiState {
        val yearsWithNotes = counts.keys.map { it.year }.distinct().sortedDescending()
        val monthsWithNotes = counts.keys.mapTo(hashSetOf()) { YearMonth.from(it) }

        val base = HeatmapPageUiState(
            filter = currentFilter,
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
                maxCount = counts.values.maxOrNull() ?: 0
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

    private companion object {
        const val SWITCH_INDICATOR_MS = 200L
    }
}

data class HeatmapPageUiState(
    val filter: HeatmapFilter = HeatmapFilter.All,
    /** 周网格数据（全部笔记 / 整年视图），一列一周 */
    val weeks: List<List<HeatmapDay>> = emptyList(),
    /** 单月日历数据，一行一周，月外日期为 null */
    val monthRows: List<List<HeatmapDay?>> = emptyList(),
    val maxCount: Int = 0,
    /** 有笔记的年份（新→旧），供年份选择 */
    val yearsWithNotes: List<Int> = emptyList(),
    /** 有笔记的月份，供月份面板置灰 */
    val monthsWithNotes: Set<YearMonth> = emptySet(),
    val isLoaded: Boolean = false,
    /** 筛选切换中的过渡加载态 */
    val isSwitching: Boolean = false
)
