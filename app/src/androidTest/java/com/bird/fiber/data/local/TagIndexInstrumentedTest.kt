package com.bird.fiber.data.local

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bird.fiber.data.local.library.*
import com.bird.fiber.domain.markdown.TagParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagIndexInstrumentedTest {
    private lateinit var database: FiberDatabase
    private lateinit var writer: MarkdownIndexWriter
    private val files get() = database.markdownFileDao()
    private val tags get() = database.tagDao()

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FiberDatabase::class.java).build()
        database.libraryDao().insertLibrary(LibraryEntity("a", "工作库", "content://test/a", 0))
        database.libraryDao().insertLibrary(LibraryEntity("b", "生活库", "content://test/b", 0))
        writer = MarkdownIndexWriter(files)
    }

    @After fun close() { database.close() }

    private fun note(uri: String, library: String, body: String, modified: Long = 10) = MarkdownFileEntity(
        uri, uri, "$uri.md", modified, body.length.toLong(), library, "预览", body)

    private suspend fun query(path: String): List<MarkdownFileSummary> {
        val page = tags.notes(path).load(PagingSource.LoadParams.Refresh(null, 50, false))
        check(page is PagingSource.LoadResult.Page) { page.toString() }
        return page.data
    }

    @Test fun crossLibraryParentsDeduplicateAndUsePathBoundaries(): Unit = runBlocking {
        writer.insert(note("1", "a", "#Read/心理 #Read/历史 #Read/心理", 20))
        writer.insert(note("2", "b", "#read/心理", 20))
        writer.insert(note("3", "b", "#Reading #read_more/孩子", 30))
        assertEquals(listOf("1", "2"), query("read").map { it.uri })
        assertEquals(2, tags.observeCount("read").first())
        assertEquals(setOf("工作库", "生活库"), query("read").map { it.libraryName }.toSet())
        assertEquals(1, query("read_more").size)
        assertEquals(2, files.getNoteTags("1").size)
        assertTrue(files.getNoteTags("1").none { it.tagPath == "read" })
        assertEquals(
            listOf("Read/历史", "Read/心理"),
            TagRepository(database).noteTags("1").first().map { it.displayPath }
        )
        database.libraryDao().activateLibrary("b")
        assertEquals(2, query("read").size)
        database.libraryDao().deactivateAll()
        assertEquals(2, query("read").size)
        val read = buildTagTree(tags.observeReferences().first()).single { it.path == "read" }
        assertEquals("Read", read.displayPath)
        assertTrue(read.isVirtual)
        files.delete("1")
        files.delete("2")
        assertEquals("Read", TagRepository(database).displayPath("read").first())
    }

    @Test fun savedReplacementClearsRemovedTagsAndMarksEmptyContentComplete(): Unit = runBlocking {
        val original = note("1", "a", "#保留 #删除")
        writer.insert(original)
        writer.update(original.copy(contentText = "#保留"))
        assertTrue(query("删除").isEmpty())
        assertEquals(listOf("保留"), files.getNoteTags("1").map { it.tagPath })
        writer.update(original.copy(contentText = ""))
        assertEquals(TagParser.CURRENT_VERSION, files.getFileByUri("1")?.tagIndexVersion)
        assertEquals(0, files.countPendingTags(TagParser.CURRENT_VERSION))
        assertTrue(tags.observeReferences().first().isEmpty())
    }

    @Test fun manualParentsCanBeReleasedWithoutDeletingTheirChildren(): Unit = runBlocking {
        val repo = TagRepository(database)
        repo.add("读书")
        repo.add("读书/心理")
        repo.add("#读书/心理")
        assertEquals(2, tags.observeReferences().first().size)
        repo.delete("读书")
        val root = buildTagTree(tags.observeReferences().first()).single()
        assertTrue(root.isVirtual)
        assertFalse(root.canDelete)
        assertTrue(root.children.single().canDelete)
        assertEquals("读书/心理", root.children.single().path)
        writer.insert(note("1", "a", "#读书/心理"))
        assertEquals(0, tags.removeEmptyManual("读书/心理"))
        files.delete("1")
        assertEquals(1, tags.removeEmptyManual("读书/心理"))
        assertTrue(tags.observeReferences().first().isEmpty())
    }

    @Test fun removingOneLibraryPreservesOtherReferencesAndGlobalManualTags(): Unit = runBlocking {
        writer.insert(note("1", "a", "#共享", 20))
        writer.insert(note("2", "b", "#共享", 10))
        TagRepository(database).add("空标签")
        database.libraryDao().deleteLibraryById("a")
        assertEquals(listOf("2"), query("共享").map { it.uri })
        val tree = buildTagTree(tags.observeReferences().first())
        assertEquals(10L, tree.single { it.path == "共享" }.sortTime)
        assertTrue(tree.single { it.path == "空标签" }.canDelete)
    }

    @Test fun uriSwitchMigratesSavedContentLinksAndVersionAtomically(): Unit = runBlocking {
        writer.insert(note("old", "a", "#项目/android"))
        val old = checkNotNull(files.getFileByUri("old"))
        val links = files.getNoteTags("old")
        writer.migrateUri("old", old.copy(uri = "new"), links)
        assertNull(files.getFileByUri("old"))
        assertEquals(old.contentText, files.getFileByUri("new")?.contentText)
        assertEquals(old.tagIndexVersion, files.getFileByUri("new")?.tagIndexVersion)
        assertEquals(listOf("new"), query("项目").map { it.uri })
        writer.migrateUri("new", checkNotNull(files.getFileByUri("new")).copy(name = "same-uri"), files.getNoteTags("new"))
        assertEquals(1, query("项目").size)
        // Deliberate FK failure must roll back both parent and references.
        try {
            writer.migrateUri("new", old.copy(uri = "broken"), listOf(NoteTagEntity("new", "不存在")))
            fail("Expected foreign key violation")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertNotNull(files.getFileByUri("new"))
        assertNull(files.getFileByUri("broken"))
        assertEquals(listOf("new"), query("项目").map { it.uri })
    }

    @Test fun oldCodeDoesNotOverwriteFutureParserIndexes(): Unit = runBlocking {
        writer.insert(note("1", "a", "#已保存"))
        val future = checkNotNull(files.getFileByUri("1")).copy(tagIndexVersion = TagParser.CURRENT_VERSION + 1)
        // Update keeps the deliberately seeded future parser links.
        files.update(future)
        try {
            writer.insert(note("1", "a", "#新正文"))
            fail("Must not downgrade a newer parser index")
        } catch (_: IllegalStateException) { }
        assertEquals(future, files.getFileByUri("1"))
        assertEquals(listOf("1"), query("已保存").map { it.uri })
        assertEquals(0, files.countPendingTags(TagParser.CURRENT_VERSION))
    }
}
