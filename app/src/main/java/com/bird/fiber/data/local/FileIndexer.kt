package com.bird.fiber.data.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileIndexer internal constructor(
    private val markdownFileDao: MarkdownFileDao,
    private val scanner: MarkdownFileScanner,
    private val contentReader: MarkdownContentReader,
    private val previewReader: MarkdownPreviewReader,
    private val syncPlanner: MarkdownSyncPlanner,
    private val writer: MarkdownIndexWriter
) {

    @Inject
    constructor(markdownFileDao: MarkdownFileDao) : this(
        markdownFileDao = markdownFileDao,
        scanner = MarkdownFileScanner(),
        contentReader = MarkdownContentReader(),
        previewReader = MarkdownPreviewReader(),
        syncPlanner = MarkdownSyncPlanner(),
        writer = MarkdownIndexWriter(markdownFileDao)
    )

    private val mutex = Mutex()

    suspend fun syncLibrary(
        contentResolver: ContentResolver,
        libraryId: String,
        folderUri: String,
        onProgress: ((Int, Int) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            Timber.d("FileIndexer: start sync library=%s", libraryId)

            try {
                val filesFromSystem = scanner.scan(
                    contentResolver = contentResolver,
                    folderUri = folderUri,
                    libraryId = libraryId
                )
                val filesInDatabase = markdownFileDao.getAllByLibrary(libraryId)

                Timber.d(
                    "FileIndexer: scanned=%s, cached=%s, library=%s",
                    filesFromSystem.size,
                    filesInDatabase.size,
                    libraryId
                )

                val plan = syncPlanner.plan(filesFromSystem, filesInDatabase)
                val filesToUpsert = ArrayList<MarkdownFileEntity>(plan.entriesToUpsert.size)

                plan.entriesToUpsert.forEachIndexed { index, entry ->
                    val content = contentReader.read(
                        contentResolver = contentResolver,
                        uri = Uri.parse(entry.file.uri)
                    )
                    filesToUpsert += entry.file.toEntity(
                        contentPreview = previewReader.readFromContent(content),
                        contentText = content
                    )
                    onProgress?.invoke(index + 1, plan.entriesToUpsert.size)
                }

                if (shouldGuardMassDeletion(filesFromSystem, filesInDatabase, plan.deletedUris)) {
                    Timber.w(
                        "FileIndexer: guard mass deletion library=%s scanned=%s cached=%s deleted=%s",
                        libraryId,
                        filesFromSystem.size,
                        filesInDatabase.size,
                        plan.deletedUris.size
                    )
                    return@withLock SyncResult.Failure(
                        SyncFailure.FolderUnavailable(
                            folderUri = folderUri,
                            reason = "Mass deletion guard triggered"
                        )
                    )
                }

                writer.applySync(plan.deletedUris, filesToUpsert)

                Timber.d(
                    "FileIndexer: sync done inserted=%s updated=%s deleted=%s",
                    plan.insertedCount,
                    plan.updatedCount,
                    plan.deletedUris.size
                )

                SyncResult.Success(
                    inserted = plan.insertedCount,
                    updated = plan.updatedCount,
                    deleted = plan.deletedUris.size
                )
            } catch (e: Exception) {
                Timber.e(e, "FileIndexer: sync failed library=%s", libraryId)
                SyncResult.Failure(e.toSyncFailure(folderUri))
            }
        }
    }

    suspend fun insertFile(
        contentResolver: ContentResolver,
        libraryId: String,
        rootFolderUri: String,
        fileUri: String
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val scannedFile = scanner.scanSingleFile(
                    contentResolver = contentResolver,
                    libraryId = libraryId,
                    rootFolderUri = rootFolderUri,
                    fileUri = fileUri
                ) ?: return@withContext

                val content = contentReader.read(contentResolver, Uri.parse(scannedFile.uri))

                writer.insert(
                    scannedFile = scannedFile,
                    contentPreview = previewReader.readFromContent(content),
                    contentText = content
                )
                Timber.d("FileIndexer: inserted file=%s", scannedFile.name)
            } catch (e: Exception) {
                Timber.e(e, "FileIndexer: insert file failed uri=%s", fileUri)
            }
        }
    }

    suspend fun deleteFile(fileUri: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                writer.delete(fileUri)
                Timber.d("FileIndexer: deleted file=%s", fileUri)
            } catch (e: Exception) {
                Timber.e(e, "FileIndexer: delete file failed uri=%s", fileUri)
            }
        }
    }

    suspend fun updateFileAfterSave(
        fileUri: String,
        content: String
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val existingEntity = markdownFileDao.getFileByUri(fileUri)
                if (existingEntity == null) {
                    Timber.w("FileIndexer: update skipped, missing file=%s", fileUri)
                    return@withContext
                }

                writer.update(
                    existingEntity.copy(
                        contentPreview = previewReader.readFromContent(content),
                        contentText = content,
                        lastModified = System.currentTimeMillis(),
                        size = content.toByteArray().size.toLong()
                    )
                )
                Timber.d("FileIndexer: updated file after save=%s", fileUri)
            } catch (e: Exception) {
                Timber.e(e, "FileIndexer: update after save failed uri=%s", fileUri)
            }
        }
    }

    private fun shouldGuardMassDeletion(
        filesFromSystem: List<ScannedFile>,
        filesInDatabase: List<MarkdownFileEntity>,
        deletedUris: List<String>
    ): Boolean {
        if (filesInDatabase.size < MASS_DELETION_GUARD_MIN_EXISTING) {
            return false
        }
        if (filesFromSystem.isNotEmpty()) {
            return false
        }
        return deletedUris.size >= filesInDatabase.size
    }

    private fun Throwable.toSyncFailure(folderUri: String): SyncFailure {
        return when (this) {
            is SecurityException -> SyncFailure.PermissionLost(folderUri, this)
            is IllegalArgumentException -> SyncFailure.FolderUnavailable(folderUri, message, this)
            is android.database.CursorIndexOutOfBoundsException -> SyncFailure.FolderUnavailable(folderUri, message, this)
            else -> SyncFailure.UnknownFailure(this)
        }
    }

    companion object {
        private const val MASS_DELETION_GUARD_MIN_EXISTING = 10
    }
}

sealed interface SyncResult {
    data class Success(
        val inserted: Int,
        val updated: Int,
        val deleted: Int
    ) : SyncResult

    data class Failure(
        val error: SyncFailure
    ) : SyncResult
}

sealed interface SyncFailure {
    data class PermissionLost(
        val folderUri: String,
        val cause: Throwable? = null
    ) : SyncFailure

    data class FolderUnavailable(
        val folderUri: String,
        val reason: String? = null,
        val cause: Throwable? = null
    ) : SyncFailure

    data class UnknownFailure(
        val cause: Throwable
    ) : SyncFailure
}
