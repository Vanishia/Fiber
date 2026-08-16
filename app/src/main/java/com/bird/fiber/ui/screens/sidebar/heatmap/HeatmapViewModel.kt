package com.bird.fiber.ui.screens.sidebar.heatmap

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
 * 记录热力图 ViewModel
 *
 * 数据直接来自笔记索引表（Room Flow 自动推送更新），
 * 不涉及文件系统扫描：保存/删除/库同步都会实时反映到热力图
 */
@HiltViewModel
class HeatmapViewModel @Inject constructor(
    markdownFileDao: MarkdownFileDao
) : ViewModel() {

    val uiState: StateFlow<HeatmapUiState> = markdownFileDao.observeHeatmapEntries()
        .map { rows ->
            val counts = NoteHeatmapAggregator.countByDate(
                rows.map { HeatmapEntry(it.name, it.lastModified) }
            )
            HeatmapUiState(
                weeks = NoteHeatmapAggregator.buildWeeks(
                    counts = counts,
                    today = LocalDate.now(),
                    weeks = HEATMAP_WEEKS
                ),
                maxCount = counts.values.maxOrNull() ?: 0,
                isLoaded = true
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HeatmapUiState()
        )

    companion object {
        /** 显示最近约三个月（14 周） */
        const val HEATMAP_WEEKS = 14
    }
}

data class HeatmapUiState(
    val weeks: List<List<HeatmapDay>> = emptyList(),
    val maxCount: Int = 0,
    val isLoaded: Boolean = false
)
