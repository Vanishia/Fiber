package com.bird.fiber.data.local

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bird.fiber.data.local.library.FiberDatabase
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownFileEntity
import com.bird.fiber.data.local.library.MarkdownFileSummary
import com.bird.fiber.data.local.library.toMarkdownFileMeta
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotePinInstrumentedTest {
    @get:Rule val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), FiberDatabase::class.java
    )
    private lateinit var database: FiberDatabase
    private val files get() = database.markdownFileDao()
    private lateinit var writer: MarkdownIndexWriter

    @Before fun setup(): Unit = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, FiberDatabase::class.java
        ).build()
        database.libraryDao().insertLibrary(library("a"))
        database.libraryDao().insertLibrary(library("b"))
        writer = MarkdownIndexWriter(files)
        writer.insert(note("old", "a", 10))
        writer.insert(note("recent", "a", 30))
        writer.insert(note("other", "b", 20))
    }

    @After fun close() { database.close() }

    @Test fun pinChangesOnlyLocalFlagAndOnlyLibraryOrdering(): Unit = runBlocking {
        val original = checkNotNull(files.getFileByUri("old"))
        val source = files.getFilesByLibrarySummary("a")
        load(source)
        assertEquals(MarkdownFileDao.PinToggleResult.Pinned, files.togglePin("old"))
        assertEquals(original.copy(pinnedLibraryId = "a"), files.getFileByUri("old"))
        val libraryNotes = load(files.getFilesByLibrarySummary("a"))
        assertEquals(listOf("old", "recent"), libraryNotes.map { it.uri })
        assertTrue(libraryNotes.first().toMarkdownFileMeta().isPinned)
        assertEquals(listOf("recent", "other", "old"), load(files.getAllFilesSummary()).map { it.uri })
        assertEquals(listOf("recent", "old"), load(files.searchFilesSummary("a", "", null, null)).map { it.uri })
        assertTrue(load(database.tagDao().notes("pin")).single { it.uri == "old" }.toMarkdownFileMeta().isPinned)
        assertEquals(MarkdownFileDao.PinToggleResult.Unpinned, files.togglePin("old"))
        assertEquals(listOf("recent", "old"), load(files.getFilesByLibrarySummary("a")).map { it.uri })
    }

    @Test fun syncAndStaleSaveUseCurrentPinInsteadOfSnapshot(): Unit = runBlocking {
        val unpinnedSnapshot = checkNotNull(files.getFileByUri("old"))
        files.togglePin("old")
        writer.upsertBatch(listOf(note("old", "a", 11)))
        assertEquals("a", files.getPinnedLibraryId("old"))
        writer.update(unpinnedSnapshot.copy(contentText = "updated #pin"))
        assertEquals("a", files.getPinnedLibraryId("old"))
        val pinnedSnapshot = checkNotNull(files.getFileByUri("old"))
        assertTrue(
            files.togglePin("recent") is MarkdownFileDao.PinToggleResult.ConfirmationRequired
        )
        writer.update(pinnedSnapshot)
        assertEquals("a", files.getPinnedLibraryId("old"))
        assertEquals(MarkdownFileDao.PinToggleResult.Pinned, files.togglePin("recent", "old"))
        assertNull(files.getPinnedLibraryId("old"))
        assertEquals("a", files.getPinnedLibraryId("recent"))
        assertNotNull(files.getFileByUri("recent"))
    }

    @Test fun replacementRenameAndSameUriRenameKeepPinAndTags(): Unit = runBlocking {
        files.togglePin("old")
        writer.replaceUriWithContent("old", note("new", "a", 10))
        assertNull(files.getFileByUri("old"))
        assertEquals("a", files.getPinnedLibraryId("new"))
        val current = checkNotNull(files.getFileByUri("new"))
        writer.migrateUri("new", current.copy(name = "renamed"), files.getNoteTags("new"))
        assertEquals("a", files.getPinnedLibraryId("new"))
        assertEquals(listOf("pin"), files.getNoteTags("new").map { it.tagPath })
    }

    @Test fun removingAndReimportingLibraryDoesNotRestorePin(): Unit = runBlocking {
        files.togglePin("old")
        assertTrue(
            files.togglePin("other") is MarkdownFileDao.PinToggleResult.ConfirmationRequired
        )
        files.togglePin("other", "old")
        database.libraryDao().deleteLibraryById("a")
        database.libraryDao().insertLibrary(library("a"))
        writer.insert(note("old", "a", 10))
        assertNull(files.getPinnedLibraryId("old"))
        assertEquals("b", files.getPinnedLibraryId("other"))
        files.delete("other")
        writer.insert(note("other", "b", 20))
        assertNull(files.getPinnedLibraryId("other"))
    }

    @Test fun migrationFromSixPreservesNotesAndInitializesWithoutPins() {
        migrations.createDatabase("note-pin-migration", 6).apply {
            execSQL("INSERT INTO libraries (id, name, folderUri, createdAt, lastOpenedAt, isActive) VALUES ('a', 'a', 'content://test/a', 1, 1, 0)")
            execSQL("""INSERT INTO markdown_files (uri, name, path, last_modified, size, library_id,
                content_preview, content_text, has_image, first_image_path, is_deleted, tag_index_version)
                VALUES ('old', 'old', 'old.md', 123, 10, 'a', 'preview', '#pin', 0, '', 0, 1)""")
            close()
        }
        migrations.runMigrationsAndValidate("note-pin-migration", 7, true, FiberDatabase.MIGRATION_6_7).apply {
            query("SELECT content_text, last_modified, tag_index_version, pinned_library_id FROM markdown_files").use {
                assertTrue(it.moveToFirst())
                assertEquals("#pin", it.getString(0))
                assertEquals(123L, it.getLong(1))
                assertEquals(1, it.getInt(2))
                assertTrue(it.isNull(3))
            }
            close()
        }
    }

    private suspend fun load(source: PagingSource<Int, MarkdownFileSummary>): List<MarkdownFileSummary> {
        val page = source.load(PagingSource.LoadParams.Refresh(null, 50, false))
        check(page is PagingSource.LoadResult.Page) { page.toString() }
        return page.data
    }

    private fun library(id: String) = LibraryEntity(id, id, "content://test/$id", 0)
    private fun note(uri: String, library: String, modified: Long) = MarkdownFileEntity(
        uri, uri, "$uri.md", modified, 20, library, "preview", "content #pin"
    )
}
