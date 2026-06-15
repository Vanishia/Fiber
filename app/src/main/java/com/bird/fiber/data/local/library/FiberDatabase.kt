package com.bird.fiber.data.local.library

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Fiber 主数据库
 *
 * Room 数据库实例，包含所有表：
 * - libraries（笔记库）
 * - markdown_files（文件索引）✨ 新增
 *
 * 未来可以扩展：
 * - tags（标签）
 * - attachments（附件）
 */
@Database(
    entities = [
        LibraryEntity::class,
        MarkdownFileEntity::class  // ✨ 新增
    ],
    version = 3,  // ✨ 版本升级
    exportSchema = true
)
abstract class FiberDatabase : RoomDatabase() {

    /**
     * 提供 LibraryDao
     */
    abstract fun libraryDao(): LibraryDao

    /**
     * 提供 MarkdownFileDao
     */
    abstract fun markdownFileDao(): MarkdownFileDao

    companion object {
        private const val DATABASE_NAME = "fiber_database"

        @Volatile
        private var INSTANCE: FiberDatabase? = null

        /**
         * 数据库迁移：版本 1 -> 版本 2
         *
         * 添加 markdown_files 表
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建 markdown_files 表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS markdown_files (
                        uri TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        path TEXT NOT NULL,
                        last_modified INTEGER NOT NULL,
                        size INTEGER NOT NULL,
                        library_id TEXT NOT NULL,
                        content_preview TEXT NOT NULL DEFAULT '',
                        content_text TEXT NOT NULL DEFAULT '',
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (library_id) REFERENCES libraries(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 创建索引
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_markdown_files_library_id
                    ON markdown_files(library_id)
                """.trimIndent())

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_markdown_files_last_modified
                    ON markdown_files(last_modified)
                """.trimIndent())
            }
        }

        /**
         * 数据库迁移：版本 2 -> 版本 3
         *
         * 添加全文搜索用的正文索引字段
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE markdown_files
                    ADD COLUMN content_text TEXT NOT NULL DEFAULT ''
                """.trimIndent())
            }
        }

        /**
         * 获取数据库实例（单例模式）
         */
        fun getInstance(context: Context): FiberDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FiberDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)  // ✨ 添加迁移
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * 用于测试：重置实例
         */
        fun resetInstance() {
            INSTANCE = null
        }
    }
}
