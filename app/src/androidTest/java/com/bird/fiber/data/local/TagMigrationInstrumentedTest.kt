package com.bird.fiber.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bird.fiber.data.local.library.FiberDatabase
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagMigrationInstrumentedTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FiberDatabase::class.java)

    @Test fun migrationKeepsExistingContentAndInitializesPendingVersion() {
        helper.createDatabase("tags-migration", 5).apply {
            execSQL("INSERT INTO libraries (id, name, folderUri, createdAt, lastOpenedAt, isActive) VALUES ('a', '旧库', 'content://test/a', 1, 1, 0)")
            execSQL("""INSERT INTO markdown_files (uri, name, path, last_modified, size, library_id, content_preview, content_text, has_image, first_image_path, is_deleted)
                VALUES ('note', '旧笔记', 'note.md', 123, 10, 'a', '原预览', '#原标签', 0, '', 0)""")
            close()
        }
        helper.runMigrationsAndValidate("tags-migration", 6, true, FiberDatabase.MIGRATION_5_6).apply {
            query("SELECT content_preview, content_text, last_modified, tag_index_version FROM markdown_files").use {
                assertTrue(it.moveToFirst())
                assertEquals("原预览", it.getString(0))
                assertEquals("#原标签", it.getString(1))
                assertEquals(123, it.getInt(2))
                assertEquals(0, it.getInt(3))
            }
            close()
        }
    }
}
