package com.bird.fiber.data.local.library

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 笔记库实体
 *
 * 这是 Room 数据库的表结构，用于存储多个笔记库的元数据
 * 注意：这里只存储索引信息，实际的笔记内容仍然是 MD 文件
 *
 * 原则强调：
 * - 笔记内容永远在 MD 文件中
 * - 数据库只是"加速器"和"索引"
 * - 文件可以直接在 Obsidian/VS Code 中打开
 */
@Entity(tableName = "libraries")
data class LibraryEntity(
    @PrimaryKey
    val id: String,                    // UUID，唯一标识
    val name: String,                  // 库名称，如"工作笔记"
    val folderUri: String,             // SAF 文件夹 URI
    val createdAt: Long,               // 创建时间戳
    val lastOpenedAt: Long = createdAt,// 最后打开时间
    val isActive: Boolean = false      // 是否是当前激活的库
)
