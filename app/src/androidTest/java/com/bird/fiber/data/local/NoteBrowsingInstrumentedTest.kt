package com.bird.fiber.data.local

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bird.fiber.data.local.library.FiberDatabase
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.MarkdownFileEntity
import com.bird.fiber.data.local.library.MarkdownFileSummary
import com.bird.fiber.utils.ANY_QUICK_NOTE_GLOB
import com.bird.fiber.utils.quickNoteGlobForDate
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 使用独立的真实 Room 数据库验证全部笔记与按日浏览，不修改应用中的笔记库。 */
@RunWith(AndroidJUnit4::class)
class NoteBrowsingInstrumentedTest {
    private lateinit var database: FiberDatabase
    private val day = LocalDate.of(2026, 8, 1)
    private val zone = ZoneId.systemDefault()
    private val dayStart get() = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val nextDayStart get() = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    @Before
    fun setUp(): Unit = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FiberDatabase::class.java
        ).build()
        database.libraryDao().insertLibrary(LibraryEntity("a", "工作库", "content://test/a", 0))
        database.libraryDao().insertLibrary(LibraryEntity("b", "生活库", "content://test/b", 0))
        database.markdownFileDao().insertAll(listOf(
            note("26-08-01_09-00-00", "a", nextDayStart + 1_000),
            note("普通笔记", "b", dayStart + 2_000),
            note("26-08-02_09-00-00", "b", dayStart + 1_000),
            note("次日零点", "a", nextDayStart),
            note("已删除笔记", "b", dayStart + 3_000).copy(isDeleted = 1)
        ))
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun allNotesIncludesBothLibrariesAndExcludesDeletedNotes(): Unit = runBlocking {
        val notes = load(database.markdownFileDao().getAllFilesSummary())
        assertEquals(listOf("26-08-01_09-00-00", "次日零点", "普通笔记", "26-08-02_09-00-00"), notes.map { it.name })
        assertEquals(setOf("工作库", "生活库"), notes.map { it.libraryName }.toSet())
        assertTrue(notes.all { it.contentPreview == "预览：${it.name}" })
        assertTrue(notes.all { it.matchSnippet == null })
    }

    @Test
    fun dayNotesUsesQuickNoteCreationDateAndOtherNotesModificationDate(): Unit = runBlocking {
        val notes = load(database.markdownFileDao().getFilesByDaySummary(
            quickNoteGlobForDate(day), ANY_QUICK_NOTE_GLOB, dayStart, nextDayStart
        ))
        assertEquals(listOf("26-08-01_09-00-00", "普通笔记"), notes.map { it.name })
        assertEquals(setOf("a", "b"), notes.map { it.libraryId }.toSet())
    }

    @Test
    fun dayWithoutNotesReturnsAnEmptyPage(): Unit = runBlocking {
        val emptyDay = day.minusDays(1)
        val notes = load(database.markdownFileDao().getFilesByDaySummary(
            quickNoteGlobForDate(emptyDay), ANY_QUICK_NOTE_GLOB,
            emptyDay.atStartOfDay(zone).toInstant().toEpochMilli(), dayStart
        ))
        assertTrue(notes.isEmpty())
    }

    private suspend fun load(source: PagingSource<Int, MarkdownFileSummary>): List<MarkdownFileSummary> {
        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 40, placeholdersEnabled = false))
        check(result is PagingSource.LoadResult.Page) { "笔记分页查询失败：$result" }
        return result.data
    }

    private fun note(name: String, libraryId: String, modified: Long) = MarkdownFileEntity(
        uri = "content://test/$libraryId/$name.md",
        name = name,
        path = "$name.md",
        lastModified = modified,
        size = 100,
        libraryId = libraryId,
        contentPreview = "预览：$name",
        contentText = "正文：$name"
    )
}
