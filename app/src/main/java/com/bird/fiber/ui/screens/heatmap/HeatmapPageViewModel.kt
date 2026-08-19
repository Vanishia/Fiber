package com.bird.fiber.ui.screens.heatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.domain.heatmap.HeatmapDay
import com.bird.fiber.domain.heatmap.HeatmapEntry
import com.bird.fiber.domain.heatmap.NoteHeatmapAggregator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 热力图页面 ViewModel
 *
 * 与侧边栏一样直接订阅笔记索引表，区别是覆盖最近一年（约 53 周）
 */
@HiltViewModel
class HeatmapPageViewModel @Inject constructor(
    markdownFileDao: MarkdownFileDao
) : ViewModel() {

    val uiState: StateFlow<HeatmapPageUiState> = markdownFileDao.observeHeatmapEntries()
        .map { rows ->
            val counts = NoteHeatmapAggregator.countByDate(
                rows.map { HeatmapEntry(it.name, it.lastModified) }
            )
            val today = LocalDate.now()
            HeatmapPageUiState(
                weeks = NoteHeatmapAggregator.buildWeeks(
                    counts = counts,
                    today = today,
                    weeks = NoteHeatmapAggregator.recentYearWeekCount(today)
                ),
                maxCount = counts.values.maxOrNull() ?: 0,
                isLoaded = true
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HeatmapPageUiState()
        )
}

data class HeatmapPageUiState(
    val weeks: List<List<HeatmapDay>> = emptyList(),
    val maxCount: Int = 0,
    val isLoaded: Boolean = false
)
