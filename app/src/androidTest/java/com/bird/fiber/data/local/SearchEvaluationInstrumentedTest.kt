package com.bird.fiber.data.local

import android.os.SystemClock
import android.util.Log
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.bird.fiber.data.local.library.FiberDatabase
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.MarkdownFileEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchEvaluationInstrumentedTest {

    private lateinit var database: FiberDatabase
    private lateinit var context: Context
    private lateinit var databaseName: String

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "search-evaluation-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, FiberDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        database.libraryDao().insertLibrary(
            LibraryEntity(
                id = LIBRARY_ID,
                name = "搜索评估库",
                folderUri = "content://search-evaluation",
                createdAt = 0L,
                isActive = true
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun likeSearch_preservesChineseAndArbitrarySubstringSemantics() = runBlocking {
        database.markdownFileDao().insertAll(evaluationCorpus())

        assertEquals(listOf("中文正文"), searchNames("人民共和"))
        assertEquals(listOf("observability"), searchNames("serv"))
        assertEquals(listOf("季度计划"), searchNames("roadmap"))
        assertEquals(listOf("meeting-notes"), searchNames("会议"))

        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL("CREATE VIRTUAL TABLE fts_eval USING fts4(content_text)")
        sqlite.execSQL("INSERT INTO fts_eval(content_text) VALUES (?)", arrayOf("observability"))
        val ftsSubstringMatches = sqlite.query(
            "SELECT COUNT(*) FROM fts_eval WHERE fts_eval MATCH ?",
            arrayOf("serv")
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

        assertEquals(0, ftsSubstringMatches)
    }

    @LargeTest
    @Test
    fun benchmarkLikeSearch_5000Files100Mb_logsP95() = runBlocking {
        insertBenchmarkCorpus()
        val databaseSize = database.openHelper.writableDatabase.query(
            "SELECT SUM(LENGTH(content_text)) FROM markdown_files"
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
        assertTrue(databaseSize >= TARGET_CONTENT_BYTES)

        val results = listOf("人民共和", "serv", "roadmap", "needle").associateWith { query ->
            repeat(WARM_UP_RUNS) { searchUris(query, relevanceSort = true) }
            val samples = List(MEASURED_RUNS) {
                val start = SystemClock.elapsedRealtimeNanos()
                searchUris(query, relevanceSort = true)
                (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
            }.sorted()
            samples[((samples.size - 1) * 0.95).toInt()]
        }

        Log.i(
            TAG,
            "LIKE_SEARCH_BENCHMARK files=$FILE_COUNT contentBytes=$databaseSize p95Ms=$results"
        )
    }

    private suspend fun insertBenchmarkCorpus() {
        val filler = "x".repeat(CONTENT_BYTES_PER_FILE - 32)
        (0 until FILE_COUNT).chunked(INSERT_BATCH_SIZE).forEach { indexes ->
            database.markdownFileDao().insertAll(
                indexes.map { index ->
                    val marker = when (index % 4) {
                        0 -> "中华人民共和国 needle"
                        1 -> "observability service"
                        2 -> "quarterly roadmap"
                        else -> "普通正文"
                    }
                    MarkdownFileEntity(
                        uri = "content://search-evaluation/$index.md",
                        name = if (index == 0) "meeting-notes" else "note-$index",
                        path = if (index == 1) "projects/roadmap/note-$index.md" else "notes/note-$index.md",
                        lastModified = index.toLong(),
                        size = CONTENT_BYTES_PER_FILE.toLong(),
                        libraryId = LIBRARY_ID,
                        contentPreview = marker,
                        contentText = "$marker $filler",
                        isDeleted = 0
                    )
                }
            )
        }
    }

    private fun evaluationCorpus(): List<MarkdownFileEntity> = listOf(
        entity(name = "中文正文", path = "notes/chinese.md", content = "中华人民共和国"),
        entity(name = "observability", path = "notes/english.md", content = "metrics and tracing"),
        entity(name = "季度计划", path = "projects/roadmap/quarter.md", content = "milestones"),
        entity(name = "meeting-notes", path = "work/meeting.md", content = "项目会议记录")
    )

    private fun entity(name: String, path: String, content: String) = MarkdownFileEntity(
        uri = "content://search-evaluation/$name.md",
        name = name,
        path = path,
        lastModified = 0L,
        size = content.length.toLong(),
        libraryId = LIBRARY_ID,
        contentPreview = content,
        contentText = content,
        isDeleted = 0
    )

    private fun searchNames(query: String): List<String> {
        val pattern = "%$query%"
        return database.openHelper.readableDatabase.query(
            """
            SELECT name FROM markdown_files
            WHERE is_deleted = 0
              AND (name LIKE ? OR path LIKE ? OR content_text LIKE ?)
            ORDER BY name
            """.trimIndent(),
            arrayOf(pattern, pattern, pattern)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun searchUris(query: String, relevanceSort: Boolean): List<String> {
        val pattern = "%$query%"
        val orderBy = if (relevanceSort) {
            """
            CASE
                WHEN name = ? OR name = ? THEN 0
                WHEN name LIKE ? THEN 1
                WHEN path LIKE ? THEN 2
                ELSE 3
            END, last_modified DESC
            """.trimIndent()
        } else {
            "last_modified DESC"
        }
        val args = if (relevanceSort) {
            arrayOf(pattern, pattern, pattern, query, "$query.md", "$query%", pattern)
        } else {
            arrayOf(pattern, pattern, pattern)
        }
        return database.openHelper.readableDatabase.query(
            """
            SELECT uri FROM markdown_files
            WHERE is_deleted = 0
              AND (name LIKE ? OR path LIKE ? OR content_text LIKE ?)
            ORDER BY $orderBy
            LIMIT 40
            """.trimIndent(),
            args
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    companion object {
        private const val TAG = "SearchEvaluation"
        private const val LIBRARY_ID = "search-evaluation-library"
        private const val FILE_COUNT = 5_000
        private const val CONTENT_BYTES_PER_FILE = 20_480
        private const val TARGET_CONTENT_BYTES = 100_000_000L
        private const val INSERT_BATCH_SIZE = 50
        private const val WARM_UP_RUNS = 5
        private const val MEASURED_RUNS = 20
    }
}
