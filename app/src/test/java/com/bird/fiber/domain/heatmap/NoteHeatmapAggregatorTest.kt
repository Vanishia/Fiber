package com.bird.fiber.domain.heatmap

import com.bird.fiber.utils.parseQuickNoteDate
import com.bird.fiber.utils.quickNoteGlobForDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class NoteHeatmapAggregatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun millisOf(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `解析快速笔记文件名得到创建日期`() {
        assertEquals(LocalDate.of(2026, 1, 29), parseQuickNoteDate("26-01-29_02-38-10"))
        assertEquals(LocalDate.of(2026, 8, 16), parseQuickNoteDate("26-08-16_21-58-50"))
    }

    @Test
    fun `非法日期或非时间戳文件名解析为 null`() {
        assertNull(parseQuickNoteDate("26-13-01_00-00-00"))
        assertNull(parseQuickNoteDate("26-01-32_00-00-00"))
        assertNull(parseQuickNoteDate("我的笔记"))
        assertNull(parseQuickNoteDate("2026-01-29_02-38-10"))
    }

    @Test
    fun `GLOB 模式匹配对应日期的快速笔记文件名`() {
        val glob = quickNoteGlobForDate(LocalDate.of(2026, 7, 26))
        assertEquals("26-07-26_[0-9][0-9]-[0-9][0-9]-[0-9][0-9]", glob)
        // 转成正则验证语义：GLOB 的 [0-9] 等价于正则字符类
        val regex = Regex(glob.replace("[0-9]", "[0-9]"))
        assertTrue(regex.matches("26-07-26_02-38-10"))
        assertFalse(regex.matches("26-07-27_02-38-10"))
        assertFalse(regex.matches("26-07-26"))
    }

    @Test
    fun `生效日期优先取文件名创建日期`() {
        val modified = millisOf(LocalDate.of(2026, 8, 1))
        val date = NoteHeatmapAggregator.effectiveDate("26-01-29_02-38-10", modified, zone)
        assertEquals(LocalDate.of(2026, 1, 29), date)
    }

    @Test
    fun `生效日期回退为最后修改日期`() {
        val modified = millisOf(LocalDate.of(2026, 8, 1))
        val date = NoteHeatmapAggregator.effectiveDate("我的笔记", modified, zone)
        assertEquals(LocalDate.of(2026, 8, 1), date)
    }

    @Test
    fun `按生效日期聚合同一天的多条笔记`() {
        val entries = listOf(
            HeatmapEntry("26-08-01_10-00-00", millisOf(LocalDate.of(2026, 8, 5))),
            HeatmapEntry("26-08-01_11-00-00", millisOf(LocalDate.of(2026, 8, 6))),
            HeatmapEntry("标题笔记", millisOf(LocalDate.of(2026, 8, 1)))
        )
        val counts = NoteHeatmapAggregator.countByDate(entries, zone)
        assertEquals(3, counts[LocalDate.of(2026, 8, 1)])
    }

    @Test
    fun `周网格以今天为最后一列且周一开头`() {
        val today = LocalDate.of(2026, 8, 16) // 周日
        val weeks = NoteHeatmapAggregator.buildWeeks(emptyMap(), today, weeks = 14)

        assertEquals(14, weeks.size)
        weeks.forEach { week -> assertEquals(7, week.size) }

        val lastWeek = weeks.last()
        // 今天是周日，落在最后一列最后一行
        assertEquals(today, lastWeek.last().date)
        assertFalse(lastWeek.last().isFuture)
        // 每周第一天是周一
        weeks.forEach { week -> assertEquals(1, week.first().date.dayOfWeek.value) }
    }

    @Test
    fun `未来日期标记为 isFuture`() {
        val today = LocalDate.of(2026, 8, 14) // 周五
        val weeks = NoteHeatmapAggregator.buildWeeks(emptyMap(), today, weeks = 2)
        val lastWeek = weeks.last()

        assertEquals(today, lastWeek[4].date)
        assertFalse(lastWeek[4].isFuture)
        assertTrue(lastWeek[5].isFuture)
        assertTrue(lastWeek[6].isFuture)
    }

    @Test
    fun `网格中的日期带正确的计数`() {
        val today = LocalDate.of(2026, 8, 16)
        val counts = mapOf(today.minusDays(1) to 5)
        val weeks = NoteHeatmapAggregator.buildWeeks(counts, today, weeks = 2)

        val day = weeks.flatten().first { it.date == today.minusDays(1) }
        assertEquals(5, day.count)
    }

    @Test
    fun `无笔记或无最大值时分档为 0`() {
        assertEquals(0, NoteHeatmapAggregator.colorLevel(0, 10))
        assertEquals(0, NoteHeatmapAggregator.colorLevel(5, 0))
    }

    @Test
    fun `单日 32 条封顶为最深色且超出不再加深`() {
        val max = 45
        assertEquals(4, NoteHeatmapAggregator.colorLevel(32, max))
        assertEquals(4, NoteHeatmapAggregator.colorLevel(45, max))
        // 32 封顶后均分：31 条落在第三档，不会被 45 条压得更浅
        assertEquals(3, NoteHeatmapAggregator.colorLevel(31, max))
        assertEquals(2, NoteHeatmapAggregator.colorLevel(20, max))
        assertEquals(1, NoteHeatmapAggregator.colorLevel(5, max))
    }

    @Test
    fun `单日最高不足 20 条时只有三个颜色层级`() {
        val max = 10
        // 最高也只有第三档（第二深的颜色），不会出最深色
        assertEquals(3, NoteHeatmapAggregator.colorLevel(10, max))
        assertEquals(2, NoteHeatmapAggregator.colorLevel(7, max))
        assertEquals(1, NoteHeatmapAggregator.colorLevel(1, max))
    }

    @Test
    fun `单日最高达到 20 条时启用四个颜色层级`() {
        val max = 20
        assertEquals(4, NoteHeatmapAggregator.colorLevel(20, max))
    }

    @Test
    fun `年网格覆盖整年且年外日期不渲染`() {
        val today = LocalDate.of(2026, 8, 19)
        val counts = mapOf(LocalDate.of(2025, 12, 29) to 3, LocalDate.of(2026, 1, 5) to 2)
        val weeks = NoteHeatmapAggregator.buildYearWeeks(counts, 2026, today)

        // 2026-01-01 是周四，其周一是 2025-12-29
        assertEquals(LocalDate.of(2025, 12, 29), weeks.first().first().date)
        // 年外日期标记占位且不计数
        assertTrue(weeks.first().first().isFuture)
        assertEquals(0, weeks.first().first().count)
        // 年内日期正常计数
        val jan5 = weeks.flatten().first { it.date == LocalDate.of(2026, 1, 5) }
        assertEquals(2, jan5.count)
        assertFalse(jan5.isFuture)
        // 今天之后的日期占位
        assertTrue(weeks.flatten().first { it.date == LocalDate.of(2026, 12, 31) }.isFuture)
    }

    @Test
    fun `月网格每行一周且月外日期为 null`() {
        val today = LocalDate.of(2026, 8, 31)
        val yearMonth = java.time.YearMonth.of(2026, 8)
        val counts = mapOf(LocalDate.of(2026, 8, 15) to 4)
        val rows = NoteHeatmapAggregator.buildMonthRows(counts, yearMonth, today)

        // 2026-08-01 是周六，首行前几天是 7 月的占位
        assertNull(rows.first()[0]) // 周一
        assertNull(rows.first()[4]) // 周五
        assertEquals(LocalDate.of(2026, 8, 1), rows.first()[5]?.date)
        // 每行 7 天，行内周一开头
        rows.forEach { row -> assertEquals(7, row.size) }
        val aug15 = rows.flatten().filterNotNull().first { it.date == LocalDate.of(2026, 8, 15) }
        assertEquals(4, aug15.count)
    }

    @Test
    fun `最近一年周列数覆盖 365 天`() {
        val today = LocalDate.of(2026, 8, 19) // 周三
        val weeks = NoteHeatmapAggregator.recentYearWeekCount(today)
        val grid = NoteHeatmapAggregator.buildWeeks(emptyMap(), today, weeks)
        // 364 天前落在第一列周内（周一对齐，起点可能再往前挪几天）
        val first = grid.first().first().date
        val yearAgo = today.minusDays(364)
        assertFalse(yearAgo.isBefore(first))
        assertFalse(yearAgo.isAfter(first.plusDays(6)))
        assertEquals(today, grid.last()[today.dayOfWeek.value - 1].date)
    }
}
