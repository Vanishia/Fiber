package com.bird.fiber.data.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.bird.fiber.utils.MarkdownUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileIndexer internal constructor(
    private val markdownFileDao: MarkdownFileDao,
    private val scanner: MarkdownFileScanner,
    private val contentReader: MarkdownContentReader,
    private val previewReader: MarkdownPreviewReader,
    private val syncPlanner: MarkdownSyncPlanner,
    private val writer: MarkdownIndexWriter,
    private val lockMetrics: IndexLockMetrics = TimberIndexLockMetrics
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

    /**
     * 统计待重建索引（摘要为空）的笔记数
     *
     * 数据库升级迁移会清空全库摘要，启动时据此判断是否需要一次性全库迁移
     */
    suspend fun countPendingReindex(): Int = withContext(Dispatchers.IO) {
        markdownFileDao.countMissingPreview()
    }

    suspend fun syncLibrary(
        contentResolver: ContentResolver,
        libraryId: String,
        folderUri: String,
        reason: String = "library-sync",
        onProgress: ((Int, Int) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        withIndexLock(libraryId, reason) {
            Timber.d("FileIndexer: start sync library=%s", libraryId)

            try {
                val filesFromSystem = scanner.scan(
                    contentResolver = contentResolver,
                    folderUri = folderUri,
                    libraryId = libraryId
                )
                val filesInDatabase = markdownFileDao.getIndexSnapshotsByLibrary(libraryId)

                Timber.d(
                    "FileIndexer: scanned=%s, cached=%s, library=%s",
                    filesFromSystem.size,
                    filesInDatabase.size,
                    libraryId
                )

                val plan = syncPlanner.plan(filesFromSystem, filesInDatabase)
                onProgress?.invoke(0, plan.entriesToUpsert.size)

                if (shouldGuardMassDeletion(filesFromSystem, filesInDatabase, plan.deletedUris)) {
                    Timber.w(
                        "FileIndexer: guard mass deletion library=%s scanned=%s cached=%s deleted=%s",
                        libraryId,
                        filesFromSystem.size,
                        filesInDatabase.size,
                        plan.deletedUris.size
                    )
                    return@withIndexLock SyncResult.Failure(
                        SyncFailure.FolderUnavailable(
                            folderUri = folderUri,
                            reason = "Mass deletion guard triggered"
                        )
                    )
                }

                var processedCount = 0
                plan.entriesToUpsert.chunked(SYNC_BATCH_SIZE).forEach { batch ->
                    val filesToUpsert = batch.map { entry ->
                        val content = contentReader.read(
                            contentResolver = contentResolver,
                            uri = Uri.parse(entry.file.uri)
                        )
                        processedCount++
                        onProgress?.invoke(processedCount, plan.entriesToUpsert.size)
                        entry.file.toEntity(
                            contentPreview = previewReader.readFromContent(content),
                            contentText = content,
                            hasImage = MarkdownUtils.containsImage(content),
                            firstImagePath = extractFirstImagePath(content)
                        )
                    }
                    writer.upsertBatch(filesToUpsert)
                }
                writer.deleteMissing(plan.deletedUris)

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
            } catch (e: CancellationException) {
                throw e
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
    ): Boolean = withContext(Dispatchers.IO) {
        withIndexLock(libraryId, "file-create-index") {
            try {
                val scannedFile = scanner.scanSingleFile(
                    contentResolver = contentResolver,
                    libraryId = libraryId,
                    rootFolderUri = rootFolderUri,
                    fileUri = fileUri
                ) ?: return@withIndexLock false

                val content = contentReader.read(contentResolver, Uri.parse(scannedFile.uri))

                writer.insert(
                    scannedFile = scannedFile,
                    contentPreview = previewReader.readFromContent(content),
                    contentText = content,
                    hasImage = MarkdownUtils.containsImage(content),
                    firstImagePath = extractFirstImagePath(content)
                )
                Timber.d("FileIndexer: inserted file=%s", scannedFile.name)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "FileIndexer: insert file failed uri=%s", fileUri)
                false
            }
        }
    }

    /**
     * 查询文件当前索引所属的库 ID，用于重命名后在原库重建索引
     */
    suspend fun getIndexedLibraryId(fileUri: String): String? = withContext(Dispatchers.IO) {
        markdownFileDao.getFileByUri(fileUri)?.libraryId
    }

    suspend fun deleteFile(fileUri: String) = withContext(Dispatchers.IO) {
        withIndexLock(null, "file-delete-index") {
            try {
                writer.delete(fileUri)
                Timber.d("FileIndexer: deleted file=%s", fileUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "FileIndexer: delete file failed uri=%s", fileUri)
            }
        }
    }

    suspend fun updateFileAfterSave(
        fileUri: String,
        content: String,
        size: Long? = null
    ) = withContext(Dispatchers.IO) {
        withIndexLock(null, "file-save-index") {
            try {
                val existingEntity = markdownFileDao.getFileByUri(fileUri)
                if (existingEntity == null) {
                    Timber.w("FileIndexer: update skipped, missing file=%s", fileUri)
                    return@withIndexLock
                }

                writer.update(
                    existingEntity.copy(
                        contentPreview = previewReader.readFromContent(content),
                        contentText = content,
                        hasImage = MarkdownUtils.containsImage(content),
                        firstImagePath = extractFirstImagePath(content),
                        lastModified = System.currentTimeMillis(),
                        // 调用方未传 size 时保留旧值，避免为计算字节数再复制一次大正文；
                        // 下次库同步会用文件系统的真实大小修正
                        size = size ?: existingEntity.size
                    )
                )
                Timber.d("FileIndexer: updated file after save=%s", fileUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "FileIndexer: update after save failed uri=%s", fileUri)
            }
        }
    }

    /**
     * 提取正文第一张图片的可加载路径（列表缩略图用）
     *
     * 本地附件返回归一化相对路径（attachments/xxx.jpg），外部图片返回完整 URL；
     * 无图片或路径非法时返回空串
     */
    private fun extractFirstImagePath(content: String): String {
        val destination = MarkdownUtils.extractImageDestinations(content).firstOrNull()
            ?: return ""
        return normalizeAttachmentPath(destination)
            ?: destination.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()
    }

    private fun shouldGuardMassDeletion(
        filesFromSystem: List<ScannedFile>,
        filesInDatabase: List<com.bird.fiber.data.local.library.MarkdownIndexSnapshot>,
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

    private suspend fun <T> withIndexLock(
        libraryId: String?,
        reason: String,
        block: suspend () -> T
    ): T {
        val waitStartedNanos = System.nanoTime()
        try {
            mutex.lock()
        } catch (e: CancellationException) {
            recordLockMetric(
                IndexLockMetric(
                    libraryId = libraryId,
                    reason = reason,
                    waitMillis = elapsedMillis(waitStartedNanos),
                    holdMillis = 0L,
                    acquired = false
                )
            )
            throw e
        }

        val acquiredNanos = System.nanoTime()
        val waitMillis = elapsedMillis(waitStartedNanos, acquiredNanos)
        try {
            return block()
        } finally {
            val holdMillis = elapsedMillis(acquiredNanos)
            mutex.unlock()
            recordLockMetric(
                IndexLockMetric(
                    libraryId = libraryId,
                    reason = reason,
                    waitMillis = waitMillis,
                    holdMillis = holdMillis,
                    acquired = true
                )
            )
        }
    }

    private fun recordLockMetric(metric: IndexLockMetric) {
        runCatching { lockMetrics.record(metric) }
            .onFailure { Timber.w(it, "FileIndexer: failed to record lock metric") }
    }

    private fun elapsedMillis(startNanos: Long, endNanos: Long = System.nanoTime()): Long {
        return (endNanos - startNanos).coerceAtLeast(0L) / 1_000_000L
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
        internal const val SYNC_BATCH_SIZE = 50
    }
}

data class IndexLockMetric(
    val libraryId: String?,
    val reason: String,
    val waitMillis: Long,
    val holdMillis: Long,
    val acquired: Boolean
)

fun interface IndexLockMetrics {
    fun record(metric: IndexLockMetric)
}

private object TimberIndexLockMetrics : IndexLockMetrics {
    override fun record(metric: IndexLockMetric) {
        Timber.d(
            "FileIndexer lock metric library=%s reason=%s waitMs=%s holdMs=%s acquired=%s",
            metric.libraryId ?: "unknown",
            metric.reason,
            metric.waitMillis,
            metric.holdMillis,
            metric.acquired
        )
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
