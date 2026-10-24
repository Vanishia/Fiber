package com.bird.fiber.ui.screens.heatmap

import com.bird.fiber.data.local.library.MarkdownHeatmapEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class HeatmapStatisticsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 24)

    private fun entry(
        name: String,
        modified: String = "2026-10-24T09:00:00",
        chars: Int = 100,
        libraryId: String = "a",
        libraryName: String = "库 A"
    ) = MarkdownHeatmapEntry(
        name,
        java.time.LocalDateTime.parse(modified).atZone(zone).toInstant().toEpochMilli(),
        chars, libraryId, libraryName
    )

    @Test
    fun `选择历史月份时各项期间统计只包含该月`() {
        val rows = listOf(
            entry("26-08-01_00-00-00"),
            entry("26-08-31_18-00-00", chars = 250, libraryId = "b", libraryName = "库 B"),
            entry("26-07-31_23-59-59"),
            entry("26-09-01_00-00-00"),
            entry("26-10-24_12-00-00")
        )
        val result = aggregateHeatmapStatistics(rows, HeatmapFilter.Month(YearMonth.of(2026, 8)), today, zone)
        assertEquals(listOf(1, 0, 0, 1), result.timeOfDayCounts)
        assertEquals(2, result.noteStats.periodCount)
        assertEquals(350L, result.noteStats.periodChars)
        assertEquals(2, result.libraryNoteCounts.sumOf { it.count })
        assertEquals(5, result.noteStats.totalCount)
        assertEquals(650L, result.noteStats.totalChars)
    }

    @Test
    fun `年份切换保留年界并排除未来日期`() {
        val rows = listOf(
            entry("25-12-31_23-59-59"),
            entry("26-01-01_06-00-00"),
            entry("26-10-24_12-00-00"),
            entry("26-10-25_18-00-00")
        )
        val current = aggregateHeatmapStatistics(rows, HeatmapFilter.Year(2026), today, zone)
        val previous = aggregateHeatmapStatistics(rows, HeatmapFilter.Year(2025), today, zone)
        assertEquals(listOf(0, 1, 1, 0), current.timeOfDayCounts)
        assertEquals(2, current.noteStats.periodCount)
        assertEquals(listOf(0, 0, 0, 1), previous.timeOfDayCounts)
        assertEquals(1, previous.noteStats.periodCount)
    }

    @Test
    fun `最近一年使用包含今天的三百六十五天范围`() {
        val rows = listOf(
            entry("普通笔记", today.minusDays(364).atTime(0, 0).toString()),
            entry("旧笔记", today.minusDays(365).atTime(0, 0).toString()),
            entry("26-10-24_06-00-00"),
            entry("26-10-25_18-00-00")
        )
        val result = aggregateHeatmapStatistics(rows, HeatmapFilter.All, today, zone)
        assertEquals(2, result.noteStats.periodCount)
        assertEquals(listOf(1, 1, 0, 0), result.timeOfDayCounts)
    }

    @Test
    fun `普通笔记按本地修改时间归属且同名库分别统计`() {
        val rows = listOf(
            entry("普通笔记", "2026-08-01T06:00:00", libraryId = "a", libraryName = "同名库"),
            entry("26-08-01_12-00-00", libraryId = "b", libraryName = "同名库"),
            entry("26-08-02_18-00-00", libraryId = "b", libraryName = "同名库")
        )
        val result = aggregateHeatmapStatistics(rows, HeatmapFilter.Month(YearMonth.of(2026, 8)), today, zone)
        assertEquals(listOf(0, 1, 1, 1), result.timeOfDayCounts)
        assertEquals(listOf(2, 1), result.libraryNoteCounts.map { it.count })
    }

    @Test
    fun `空范围返回零统计但保留全库总量`() {
        val result = aggregateHeatmapStatistics(
            listOf(entry("26-08-01_00-00-00")), HeatmapFilter.Month(YearMonth.of(2026, 7)), today, zone
        )
        assertEquals(listOf(0, 0, 0, 0), result.timeOfDayCounts)
        assertTrue(result.libraryNoteCounts.isEmpty())
        assertEquals(0, result.noteStats.periodCount)
        assertEquals(0L, result.noteStats.periodChars)
        assertEquals(1, result.noteStats.totalCount)
    }
}
