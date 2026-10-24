package com.bird.fiber.ui.screens.heatmap

import com.bird.fiber.data.local.library.LibraryNoteCount
import com.bird.fiber.data.local.library.MarkdownHeatmapEntry
import com.bird.fiber.domain.heatmap.NoteHeatmapAggregator
import com.bird.fiber.utils.parseQuickNoteHour
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class HeatmapStatistics(
    val timeOfDayCounts: List<Int>,
    val libraryNoteCounts: List<LibraryNoteCount>,
    val noteStats: NoteStats
)

/** 所有期间统计使用与热力图相同的日期口径；全库总量单独保留。 */
internal fun aggregateHeatmapStatistics(
    rows: List<MarkdownHeatmapEntry>,
    filter: HeatmapFilter,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): HeatmapStatistics {
    val start = when (filter) {
        HeatmapFilter.All -> today.minusDays(364)
        is HeatmapFilter.Year -> LocalDate.of(filter.year, 1, 1)
        is HeatmapFilter.Month -> filter.yearMonth.atDay(1)
    }
    val end = minOf(today, when (filter) {
        HeatmapFilter.All -> today
        is HeatmapFilter.Year -> LocalDate.of(filter.year, 12, 31)
        is HeatmapFilter.Month -> filter.yearMonth.atEndOfMonth()
    })
    val selected = rows.filter {
        NoteHeatmapAggregator.effectiveDate(it.name, it.lastModified, zone) in start..end
    }
    val buckets = IntArray(4)
    selected.forEach { row ->
        val hour = parseQuickNoteHour(row.name)
            ?: Instant.ofEpochMilli(row.lastModified).atZone(zone).hour
        buckets[hour / 6]++
    }
    return HeatmapStatistics(
        timeOfDayCounts = buckets.toList(),
        libraryNoteCounts = selected.groupBy { it.libraryId }.values
            .map { LibraryNoteCount(it.first().libraryName, it.size) }
            .sortedByDescending { it.count },
        noteStats = NoteStats(
            totalCount = rows.size,
            totalChars = rows.sumOf { it.charCount.toLong() },
            periodCount = selected.size,
            periodChars = selected.sumOf { it.charCount.toLong() }
        )
    )
}
