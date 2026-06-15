package com.bird.fiber.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSyncPlannerTest {

    private val planner = MarkdownSyncPlanner()

    @Test
    fun plan_newFile_marksInsert() {
        val systemFiles = listOf(scannedFile(uri = "uri-1", lastModified = 100L))

        val plan = planner.plan(systemFiles, emptyList())

        assertEquals(1, plan.insertedCount)
        assertEquals(0, plan.updatedCount)
        assertTrue(plan.deletedUris.isEmpty())
        assertEquals(listOf("uri-1"), plan.entriesToUpsert.map { it.file.uri })
        assertEquals(listOf(UpsertReason.NEW_FILE), plan.entriesToUpsert.map { it.reason })
    }

    @Test
    fun plan_modifiedFile_marksUpdate() {
        val systemFiles = listOf(scannedFile(uri = "uri-1", lastModified = 200L))
        val cachedFiles = listOf(entityFile(uri = "uri-1", lastModified = 100L, preview = "old"))

        val plan = planner.plan(systemFiles, cachedFiles)

        assertEquals(0, plan.insertedCount)
        assertEquals(1, plan.updatedCount)
        assertEquals(listOf(UpsertReason.MODIFIED), plan.entriesToUpsert.map { it.reason })
    }

    @Test
    fun plan_missingPreview_marksUpdate() {
        val systemFiles = listOf(scannedFile(uri = "uri-1", lastModified = 100L))
        val cachedFiles = listOf(entityFile(uri = "uri-1", lastModified = 100L, preview = ""))

        val plan = planner.plan(systemFiles, cachedFiles)

        assertEquals(0, plan.insertedCount)
        assertEquals(1, plan.updatedCount)
        assertEquals(listOf(UpsertReason.MISSING_PREVIEW), plan.entriesToUpsert.map { it.reason })
    }

    @Test
    fun plan_missingSearchContent_marksUpdate() {
        val systemFiles = listOf(scannedFile(uri = "uri-1", lastModified = 100L))
        val cachedFiles = listOf(
            entityFile(
                uri = "uri-1",
                lastModified = 100L,
                preview = "ready",
                contentText = ""
            )
        )

        val plan = planner.plan(systemFiles, cachedFiles)

        assertEquals(0, plan.insertedCount)
        assertEquals(1, plan.updatedCount)
        assertEquals(listOf(UpsertReason.MISSING_SEARCH_CONTENT), plan.entriesToUpsert.map { it.reason })
    }

    @Test
    fun plan_missingSystemFile_marksDelete() {
        val cachedFiles = listOf(entityFile(uri = "uri-1", lastModified = 100L))

        val plan = planner.plan(emptyList(), cachedFiles)

        assertEquals(0, plan.insertedCount)
        assertEquals(0, plan.updatedCount)
        assertEquals(listOf("uri-1"), plan.deletedUris)
    }

    @Test
    fun plan_unchangedFile_keepsPlanEmpty() {
        val systemFiles = listOf(scannedFile(uri = "uri-1", lastModified = 100L))
        val cachedFiles = listOf(entityFile(uri = "uri-1", lastModified = 100L, preview = "ready"))

        val plan = planner.plan(systemFiles, cachedFiles)

        assertEquals(0, plan.insertedCount)
        assertEquals(0, plan.updatedCount)
        assertTrue(plan.deletedUris.isEmpty())
        assertTrue(plan.entriesToUpsert.isEmpty())
    }

    private fun scannedFile(
        uri: String,
        lastModified: Long
    ) = ScannedFile(
        uri = uri,
        name = uri,
        path = "$uri.md",
        lastModified = lastModified,
        size = 1L,
        libraryId = "library-1"
    )

    private fun entityFile(
        uri: String,
        lastModified: Long,
        preview: String = "preview",
        contentText: String = "content"
    ) = com.bird.fiber.data.local.library.MarkdownFileEntity(
        uri = uri,
        name = uri,
        path = "$uri.md",
        lastModified = lastModified,
        size = 1L,
        libraryId = "library-1",
        contentPreview = preview,
        contentText = contentText,
        isDeleted = 0
    )
}
