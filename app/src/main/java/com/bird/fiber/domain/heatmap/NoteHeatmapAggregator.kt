package com.bird.fiber.domain.heatmap

import com.bird.fiber.utils.parseQuickNoteDate
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 热力图数据源条目（一条笔记的最小信息）
 *
 * @property name 文件名（不含 .md 后缀）
 * @property lastModified 最后修改时间（毫秒时间戳）
 */
data class HeatmapEntry(
    val name: String,
    val lastModified: Long
)

/**
 * 热力图网格中的一天
 *
 * @property date 日期
 * @property count 当天笔记数
 * @property isFuture 是否晚于今天（网格补齐用，不渲染）
 */
data class HeatmapDay(
    val date: LocalDate,
    val count: Int,
    val isFuture: Boolean = false
)

/**
 * 记录热力图聚合逻辑
 *
 * 日期口径：每个笔记在热力图上只出现一次——
 * 文件名符合快速笔记时间戳格式的按创建日落格，
 * 其余按最后修改日落格（解析不出创建时间时的退而求其次）
 */
object NoteHeatmapAggregator {

    /**
     * 笔记在热力图上的生效日期：优先文件名中的创建日期，回退为最后修改日期
     */
    fun effectiveDate(
        name: String,
        lastModified: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate {
        return parseQuickNoteDate(name)
            ?: Instant.ofEpochMilli(lastModified).atZone(zone).toLocalDate()
    }

    fun countByDate(
        entries: List<HeatmapEntry>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, Int> {
        return entries.groupingBy { effectiveDate(it.name, it.lastModified, zone) }.eachCount()
    }

    /**
     * 构建周网格：每列一周（周一在首行），共 [weeks] 列，[today] 落在最后一列
     *
     * 早于起始日、晚于今天的天分别以 count=0 / isFuture=true 占位
     */
    fun buildWeeks(
        counts: Map<LocalDate, Int>,
        today: LocalDate,
        weeks: Int
    ): List<List<HeatmapDay>> {
        require(weeks > 0) { "weeks must be positive" }
        // today 在最后一列中的行号（0=周一）
        val todayRow = today.dayOfWeek.value - 1
        val firstMonday = today.minusDays(((weeks - 1) * 7 + todayRow).toLong())

        return (0 until weeks).map { column ->
            (0 until 7).map { row ->
                val date = firstMonday.plusDays((column * 7 + row).toLong())
                HeatmapDay(
                    date = date,
                    count = counts[date] ?: 0,
                    isFuture = date.isAfter(today)
                )
            }
        }
    }

    /** 覆盖最近一年（[today] 前 364 天到今天）所需的周列数 */
    fun recentYearWeekCount(today: LocalDate): Int {
        val todayRow = today.dayOfWeek.value - 1
        return (364 + todayRow) / 7 + 1
    }

    /**
     * 构建某年的周网格：每列一周（周一在首行），覆盖该年 1 月 1 日起
     *
     * 未过完的年只截到 [today] 所在的周，避免后面拖着几个月的空白列；
     * 首周补齐出的年外日期、晚于今天的日期都以 isFuture=true 占位（不渲染）
     */
    fun buildYearWeeks(
        counts: Map<LocalDate, Int>,
        year: Int,
        today: LocalDate
    ): List<List<HeatmapDay>> {
        val firstDay = LocalDate.of(year, 1, 1)
        val firstMonday = firstDay.minusDays((firstDay.dayOfWeek.value - 1).toLong())
        val lastDay = minOf(LocalDate.of(year, 12, 31), today)
        if (lastDay.isBefore(firstDay)) return emptyList()
        val lastMonday = lastDay.minusDays((lastDay.dayOfWeek.value - 1).toLong())
        val weeks = ((lastMonday.toEpochDay() - firstMonday.toEpochDay()) / 7 + 1).toInt()

        return (0 until weeks).map { column ->
            (0 until 7).map { row ->
                val date = firstMonday.plusDays((column * 7 + row).toLong())
                HeatmapDay(
                    date = date,
                    count = if (date.year == year) counts[date] ?: 0 else 0,
                    isFuture = date.isAfter(today) || date.year != year
                )
            }
        }
    }

    /**
     * 构建单月日历网格：每行一周（周一在首列），月外日期以 null 占位
     */
    fun buildMonthRows(
        counts: Map<LocalDate, Int>,
        yearMonth: YearMonth,
        today: LocalDate
    ): List<List<HeatmapDay?>> {
        val firstMonday = yearMonth.atDay(1)
            .minusDays((yearMonth.atDay(1).dayOfWeek.value - 1).toLong())
        val lastDay = yearMonth.atEndOfMonth()
        val lastSunday = lastDay.plusDays(((7 - lastDay.dayOfWeek.value) % 7).toLong())
        val days = (lastSunday.toEpochDay() - firstMonday.toEpochDay() + 1).toInt()

        return (0 until days / 7).map { row ->
            (0 until 7).map { column ->
                val date = firstMonday.plusDays((row * 7 + column).toLong())
                if (YearMonth.from(date) != yearMonth) {
                    null
                } else {
                    HeatmapDay(
                        date = date,
                        count = counts[date] ?: 0,
                        isFuture = date.isAfter(today)
                    )
                }
            }
        }
    }

    /**
     * 颜色分档：0 = 无笔记，1..档数 = 由浅到深
     *
     * 规则：
     * - 单日 [FULL_COLOR_COUNT] 条封顶：达到即为最深色，其余按 32 均分，
     *   避免个别高产日（如 40+ 条）把普通日子压得过浅
     * - 范围内单日最高不足 [DEEP_LEVEL_MIN_COUNT] 条时只用 3 档（不出最深色），
     *   避免"没写多少就显示最深"，更贴合"写得越多颜色越深"的直觉
     */
    fun colorLevel(count: Int, maxCount: Int): Int {
        if (count <= 0 || maxCount <= 0) return 0
        val levelCount = if (maxCount >= DEEP_LEVEL_MIN_COUNT) LEVEL_COUNT else LEVEL_COUNT - 1
        val cap = minOf(maxCount, FULL_COLOR_COUNT)
        return ((count.toFloat() / cap) * levelCount).toInt().coerceIn(1, levelCount)
    }

    /** 单日笔记数达到该值即最深色 */
    const val FULL_COLOR_COUNT = 32

    /** 范围内单日最高达到该值才启用最深色档 */
    const val DEEP_LEVEL_MIN_COUNT = 20

    /** 颜色档数（不含无笔记的灰格） */
    const val LEVEL_COUNT = 4
}
