package com.bird.fiber.data.local

import com.bird.fiber.data.local.library.MarkdownFileEntity

class MarkdownSyncPlanner {
    fun plan(
        filesFromSystem: List<ScannedFile>,
        filesInDatabase: List<MarkdownFileEntity>
    ): SyncPlan {
        val databaseMap = filesInDatabase.associateBy { it.uri }
        val systemUris = filesFromSystem.mapTo(mutableSetOf()) { it.uri }

        var insertedCount = 0
        var updatedCount = 0
        val entriesToUpsert = mutableListOf<PlannedUpsert>()

        filesFromSystem.forEach { systemFile ->
            val cachedFile = databaseMap[systemFile.uri]
            when {
                cachedFile == null -> {
                    insertedCount++
                    entriesToUpsert += PlannedUpsert(systemFile, UpsertReason.NEW_FILE)
                }

                systemFile.lastModified > cachedFile.lastModified -> {
                    updatedCount++
                    entriesToUpsert += PlannedUpsert(systemFile, UpsertReason.MODIFIED)
                }

                cachedFile.contentPreview.isEmpty() -> {
                    updatedCount++
                    entriesToUpsert += PlannedUpsert(systemFile, UpsertReason.MISSING_PREVIEW)
                }

                cachedFile.contentText.isEmpty() -> {
                    updatedCount++
                    entriesToUpsert += PlannedUpsert(systemFile, UpsertReason.MISSING_SEARCH_CONTENT)
                }
            }
        }

        val deletedUris = filesInDatabase
            .asSequence()
            .map { it.uri }
            .filterNot(systemUris::contains)
            .toList()

        return SyncPlan(
            entriesToUpsert = entriesToUpsert,
            deletedUris = deletedUris,
            insertedCount = insertedCount,
            updatedCount = updatedCount
        )
    }
}

data class SyncPlan(
    val entriesToUpsert: List<PlannedUpsert>,
    val deletedUris: List<String>,
    val insertedCount: Int,
    val updatedCount: Int
)

data class PlannedUpsert(
    val file: ScannedFile,
    val reason: UpsertReason
)

enum class UpsertReason {
    NEW_FILE,
    MODIFIED,
    MISSING_PREVIEW,
    MISSING_SEARCH_CONTENT
}
