package com.bird.fiber.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件工具类
 *
 * 提供文件相关的格式化工具函数
 */
object FileUtils {
    private val dateFormatter: ThreadLocal<SimpleDateFormat> =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            }
        }

    /**
     * 格式化日期时间
     *
     * @param timestamp Unix 时间戳（毫秒）
     * @return 格式化后的日期时间字符串，格式：yyyy-MM-dd HH:mm
     */
    fun formatDate(timestamp: Long): String {
        return dateFormatter.get()!!.format(Date(timestamp))
    }

    /**
     * 格式化文件大小
     *
     * @param sizeBytes 文件大小（字节）
     * @return 格式化后的文件大小字符串，单位：B/KB/MB
     */
    fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> "${sizeBytes / (1024 * 1024)} MB"
        }
    }
}
