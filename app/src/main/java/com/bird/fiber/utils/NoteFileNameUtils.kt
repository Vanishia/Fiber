package com.bird.fiber.utils

import java.time.LocalDate

/**
 * 快速笔记文件名约定：yy-MM-dd_HH-mm-ss（如 26-01-29_02-38-10）
 *
 * 由 [com.bird.fiber.domain.usecase.GenerateFileNameUseCase] 生成，
 * 文件名本身即创建时间：
 * - 记录热力图据此解析创建日期
 * - 列表展示据此判断是否隐藏标题（时间戳标题没有阅读价值）
 */
private val QUICK_NOTE_PATTERN = Regex("""^\d{2}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}$""")

fun isQuickNoteFileName(fileName: String): Boolean = QUICK_NOTE_PATTERN.matches(fileName)

/**
 * 从快速笔记文件名解析创建日期；不符合命名约定或日期非法时返回 null
 *
 * 年份为两位，按 20yy 解释
 */
fun parseQuickNoteDate(fileName: String): LocalDate? {
    if (!isQuickNoteFileName(fileName)) return null
    return try {
        val year = 2000 + fileName.substring(0, 2).toInt()
        val month = fileName.substring(3, 5).toInt()
        val day = fileName.substring(6, 8).toInt()
        LocalDate.of(year, month, day)
    } catch (e: Exception) {
        null
    }
}

/**
 * 匹配任意快速笔记文件名的 SQLite GLOB 模式（与 [QUICK_NOTE_PATTERN] 等价）
 */
const val ANY_QUICK_NOTE_GLOB =
    "[0-9][0-9]-[0-9][0-9]-[0-9][0-9]_[0-9][0-9]-[0-9][0-9]-[0-9][0-9]"

/**
 * 匹配指定日期快速笔记文件名的 SQLite GLOB 模式（"当日笔记"查询用）
 */
fun quickNoteGlobForDate(date: LocalDate): String {
    val year = date.year % 100
    return "%02d-%02d-%02d_[0-9][0-9]-[0-9][0-9]-[0-9][0-9]"
        .format(year, date.monthValue, date.dayOfMonth)
}
