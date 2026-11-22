package com.bird.fiber.ui.screens.sidebar.heatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.domain.heatmap.HeatmapDay
import com.bird.fiber.domain.heatmap.HeatmapEntry
import com.bird.fiber.domain.heatmap.NoteHeatmapAggregator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 记录热力图 ViewModel
 *
 * 数据直接来自笔记索引表（Room Flow 自动推送更新），
 * 不涉及文件系统扫描：保存/删除/库同步都会实时反映到热力图
 */
@HiltViewModel
class HeatmapViewModel internal constructor(
    markdownFileDao: MarkdownFileDao,
    dispatcher: CoroutineDispatcher,
    clock: Clock
) : ViewModel() {

    @Inject constructor(markdownFileDao: MarkdownFileDao) : this(
        markdownFileDao, Dispatchers.Default, Clock.systemDefaultZone()
    )

    val uiState: StateFlow<HeatmapUiState> = combine(
        markdownFileDao.observeHeatmapEntries(),
        flow {
            while (true) {
                val now = ZonedDateTime.now(clock)
                emit(now.toLocalDate())
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(Duration.between(now, midnight).toMillis().coerceAtLeast(1))
            }
        }
    ) { rows, today ->
        val counts = NoteHeatmapAggregator.countByDate(
            rows.map { HeatmapEntry(it.name, it.lastModified) }, clock.zone
        )
        HeatmapUiState(
            weeks = NoteHeatmapAggregator.buildWeeks(
                counts = counts,
                today = today,
                weeks = HEATMAP_WEEKS
            ),
            maxCount = counts.values.maxOrNull() ?: 0,
            recordedDays = counts.keys.count { !it.isAfter(today) },
            todayChars = rows.asSequence().filter {
                NoteHeatmapAggregator.effectiveDate(it.name, it.lastModified, clock.zone) == today
            }.sumOf { it.charCount.toLong() },
            isLoaded = true
        )
    }
        .flowOn(dispatcher)
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
    val recordedDays: Int = 0,
    val todayChars: Long = 0,
    val isLoaded: Boolean = false
)
