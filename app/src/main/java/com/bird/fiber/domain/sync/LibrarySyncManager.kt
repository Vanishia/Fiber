package com.bird.fiber.domain.sync

import android.content.ContentResolver
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.local.FileIndexer
import com.bird.fiber.data.local.SyncFailure
import com.bird.fiber.data.local.SyncResult
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySyncManager @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val eventBus: EventBus,
    private val fileIndexer: FileIndexer
) {

    private val isSyncingAllLibraries = AtomicBoolean(false)

    suspend fun validateAndCleanupInvalidLibraries(contentResolver: ContentResolver): Int {
        return withContext(Dispatchers.IO) {
            libraryRepository.validateAndCleanupInvalidLibraries(contentResolver)
        }
    }

    suspend fun addLibraryAndSync(
        contentResolver: ContentResolver,
        folderName: String,
        folderUriString: String
    ): LibraryEntity {
        val existingLibrary = libraryRepository.getLibraryByFolderUri(folderUriString)
        val library = existingLibrary ?: LibraryEntity(
            id = UUID.randomUUID().toString(),
            name = folderName,
            folderUri = folderUriString,
            createdAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis(),
            isActive = false
        ).also { libraryRepository.addLibrary(it) }

        if (existingLibrary != null) {
            Timber.d("LibrarySyncManager: reuse existing library id=%s name=%s", library.id, library.name)
        }
        libraryRepository.switchLibrary(library.id)

        eventBus.emit(AppEvent.SyncStarted(library.id))

        withContext(Dispatchers.IO) {
            Timber.d("LibrarySyncManager: start sync selected library=%s", library.name)
            when (val result = fileIndexer.syncLibrary(
                contentResolver = contentResolver,
                libraryId = library.id,
                folderUri = folderUriString,
                reason = if (existingLibrary == null) "new-library" else "existing-library",
                onProgress = { current, total ->
                    eventBus.tryEmit(
                        AppEvent.SyncProgress(
                            libraryId = library.id,
                            processed = current,
                            total = total
                        )
                    )
                }
            )) {
                is SyncResult.Success -> {
                    Timber.d(
                        "LibrarySyncManager: selected library sync done name=%s inserted=%s updated=%s deleted=%s",
                        library.name,
                        result.inserted,
                        result.updated,
                        result.deleted
                    )
                }
                is SyncResult.Failure -> {
                    logSyncFailure("LibrarySyncManager: selected library sync failed name=${library.name}", result.error)
                }
            }
        }

        eventBus.emit(AppEvent.SyncCompleted(library.id))
        eventBus.emit(AppEvent.RefreshFileList)

        return library
    }

    suspend fun syncAllLibraries(
        contentResolver: ContentResolver,
        onProgress: ((libraryName: String, current: Int, total: Int) -> Unit)? = null
    ) {
        try {
            val libraries = libraryRepository.getAllLibraries().first()
            val activeLibraryId = libraryRepository.getActiveLibrary().first()?.id
            Timber.d("StartupTrace: syncAllLibraries begin count=${libraries.size}")
            Timber.d("LibrarySyncManager: start sync all count=%s", libraries.size)

            val activeLibraryChangedCount = syncLibraries(
                contentResolver = contentResolver,
                libraries = libraries,
                activeLibraryId = activeLibraryId,
                reason = "all-libraries",
                onProgress = onProgress
            )

            if (activeLibraryChangedCount > 0) {
                Timber.d("StartupTrace: emit RefreshFileList after syncAllLibraries activeLibraryChanged=$activeLibraryChangedCount activeLibraryId=$activeLibraryId")
                eventBus.emit(AppEvent.RefreshFileList)
                Timber.d("LibrarySyncManager: emit RefreshFileList after active library changed=%s", activeLibraryChangedCount)
            } else {
                Timber.d("StartupTrace: skip RefreshFileList after syncAllLibraries activeLibraryChanged=0 activeLibraryId=$activeLibraryId")
                Timber.d("LibrarySyncManager: skip RefreshFileList because active library unchanged")
            }
        } catch (e: Exception) {
            Timber.e(e, "LibrarySyncManager: sync files failed")
            throw e
        }
    }

    suspend fun syncActiveLibraryIfIdle(
        contentResolver: ContentResolver,
        onProgress: ((libraryName: String, current: Int, total: Int) -> Unit)? = null
    ) {
        if (!isSyncingAllLibraries.compareAndSet(false, true)) {
            Timber.d("StartupTrace: syncActiveLibraryIfIdle skipped because busy")
            Timber.d("LibrarySyncManager: skip active sync because another task is running")
            return
        }

        try {
            val activeLibrary = libraryRepository.getActiveLibrary().first()
            if (activeLibrary == null) {
                Timber.d("StartupTrace: syncActiveLibraryIfIdle skipped because no active library")
                return
            }

            Timber.d("StartupTrace: syncActiveLibraryIfIdle accepted id=${activeLibrary.id}")
            val activeLibraryChangedCount = syncLibraries(
                contentResolver = contentResolver,
                libraries = listOf(activeLibrary),
                activeLibraryId = activeLibrary.id,
                reason = "active-library",
                onProgress = onProgress
            )

            if (activeLibraryChangedCount > 0) {
                Timber.d("StartupTrace: emit RefreshFileList after syncActiveLibrary activeLibraryChanged=$activeLibraryChangedCount activeLibraryId=${activeLibrary.id}")
                eventBus.emit(AppEvent.RefreshFileList)
            }
        } catch (e: Exception) {
            Timber.e(e, "LibrarySyncManager: sync active library failed")
            throw e
        } finally {
            isSyncingAllLibraries.set(false)
            Timber.d("StartupTrace: syncActiveLibraryIfIdle released")
        }
    }

    suspend fun syncInactiveLibrariesIfIdle(
        contentResolver: ContentResolver,
        onProgress: ((libraryName: String, current: Int, total: Int) -> Unit)? = null
    ) {
        if (!isSyncingAllLibraries.compareAndSet(false, true)) {
            Timber.d("StartupTrace: syncInactiveLibrariesIfIdle skipped because busy")
            Timber.d("LibrarySyncManager: skip inactive sync because another task is running")
            return
        }

        try {
            val libraries = libraryRepository.getAllLibraries().first()
            val activeLibraryId = libraryRepository.getActiveLibrary().first()?.id
            val inactiveLibraries = libraries.filter { it.id != activeLibraryId }
            Timber.d("StartupTrace: syncInactiveLibrariesIfIdle accepted count=${inactiveLibraries.size}")
            syncLibraries(
                contentResolver = contentResolver,
                libraries = inactiveLibraries,
                activeLibraryId = activeLibraryId,
                reason = "inactive-libraries",
                onProgress = onProgress
            )
        } catch (e: Exception) {
            Timber.e(e, "LibrarySyncManager: sync inactive libraries failed")
            throw e
        } finally {
            isSyncingAllLibraries.set(false)
            Timber.d("StartupTrace: syncInactiveLibrariesIfIdle released")
        }
    }

    suspend fun syncAllLibrariesIfIdle(
        contentResolver: ContentResolver,
        onProgress: ((libraryName: String, current: Int, total: Int) -> Unit)? = null
    ) {
        if (!isSyncingAllLibraries.compareAndSet(false, true)) {
            Timber.d("StartupTrace: syncAllLibrariesIfIdle skipped because busy")
            Timber.d("LibrarySyncManager: skip sync all because another task is running")
            return
        }

        try {
            Timber.d("StartupTrace: syncAllLibrariesIfIdle accepted")
            syncAllLibraries(contentResolver, onProgress)
        } finally {
            isSyncingAllLibraries.set(false)
            Timber.d("StartupTrace: syncAllLibrariesIfIdle released")
        }
    }

    /**
     * 数据库升级后的一次性全库迁移
     *
     * 迁移会清空所有库的摘要触发全量重建；若沿用逐库按需同步，界面会在
     * 进度页与列表间来回闪烁（一个库迁完、切到另一个库才开始迁）。这里在
     * 进入应用时检测待重建数量，达到阈值就一次性迁移所有库：全程只发一对
     * SyncStarted/SyncCompleted，进度按全库聚合，期间切换库主界面保持进度页
     *
     * @return true 表示执行了迁移，调用方应跳过常规启动同步
     */
    suspend fun reindexAllLibrariesIfNeeded(contentResolver: ContentResolver): Boolean {
        val pending = fileIndexer.countPendingReindex()
        if (pending < REINDEX_PROGRESS_MIN_TOTAL) {
            return false
        }
        if (!isSyncingAllLibraries.compareAndSet(false, true)) {
            Timber.d("LibrarySyncManager: skip reindex-all because another task is running")
            return false
        }

        try {
            val libraries = libraryRepository.getAllLibraries().first()
            if (libraries.isEmpty()) return false
            val activeLibraryId = libraryRepository.getActiveLibrary().first()?.id
            Timber.d(
                "LibrarySyncManager: reindex all libraries begin count=%s pending=%s",
                libraries.size, pending
            )

            val progressLibraryId = activeLibraryId ?: libraries.first().id
            eventBus.emit(AppEvent.SyncStarted(progressLibraryId, isReindex = true))

            // 全库聚合进度：总数随各库扫描完成逐步累加，已完成部分跨库累计
            var completedInPreviousLibraries = 0
            var aggregatedTotal = 0
            var currentLibraryTotal = 0

            libraries.forEach { library ->
                when (val result = fileIndexer.syncLibrary(
                    contentResolver = contentResolver,
                    libraryId = library.id,
                    folderUri = library.folderUri,
                    reason = "db-migration",
                    onProgress = { current, total ->
                        if (total != currentLibraryTotal) {
                            aggregatedTotal += total - currentLibraryTotal
                            currentLibraryTotal = total
                        }
                        // 节流规则与逐库同步一致（首尾必发、其余每 5 个一次）
                        val shouldReport = current == 0 || current == total ||
                            current % REINDEX_PROGRESS_EVERY == 0
                        if (shouldReport) {
                            eventBus.tryEmit(
                                AppEvent.SyncProgress(
                                    libraryId = library.id,
                                    processed = completedInPreviousLibraries + current,
                                    total = aggregatedTotal
                                )
                            )
                        }
                    }
                )) {
                    is SyncResult.Success -> {
                        Timber.d(
                            "LibrarySyncManager: migration sync done name=%s inserted=%s updated=%s deleted=%s",
                            library.name, result.inserted, result.updated, result.deleted
                        )
                    }
                    is SyncResult.Failure -> {
                        logSyncFailure("LibrarySyncManager: migration sync failed name=${library.name}", result.error)
                    }
                }
                completedInPreviousLibraries += currentLibraryTotal
                currentLibraryTotal = 0
            }

            eventBus.emit(AppEvent.SyncCompleted(progressLibraryId))
            eventBus.emit(AppEvent.RefreshFileList)
            Timber.d("LibrarySyncManager: reindex all libraries done")
            return true
        } finally {
            isSyncingAllLibraries.set(false)
        }
    }

    private suspend fun syncLibraries(
        contentResolver: ContentResolver,
        libraries: List<LibraryEntity>,
        activeLibraryId: String?,
        reason: String,
        onProgress: ((libraryName: String, current: Int, total: Int) -> Unit)?
    ): Int {
        var activeLibraryChangedCount = 0

        libraries.forEach { library ->
            val folderUri = library.folderUri
            Timber.d("StartupTrace: sync library begin id=${library.id} name=${library.name}")
            // 只有全量重建索引（如数据库升级回填，待处理数达到阈值）才广播进度事件，
            // 让主界面显示进度页；少量增量更新静默完成，避免界面无意义闪烁
            var reindexVisible = false
            when (val result = fileIndexer.syncLibrary(
                contentResolver = contentResolver,
                libraryId = library.id,
                folderUri = folderUri,
                reason = reason,
                onProgress = { current, total ->
                    // 进度事件做节流（首尾必发、其余每 5 个发一次），
                    // 防止事件洪峰挤掉缓冲里的 SyncStarted（缓冲区溢出丢弃最旧）
                    val shouldReport = current == 0 || current == total ||
                        current % REINDEX_PROGRESS_EVERY == 0
                    if (total >= REINDEX_PROGRESS_MIN_TOTAL && shouldReport) {
                        if (!reindexVisible) {
                            reindexVisible = true
                            eventBus.tryEmit(AppEvent.SyncStarted(library.id, isReindex = true))
                        }
                        eventBus.tryEmit(
                            AppEvent.SyncProgress(
                                libraryId = library.id,
                                processed = current,
                                total = total
                            )
                        )
                    }
                    onProgress?.invoke(library.name, current, total)
                }
            )) {
                is SyncResult.Success -> {
                    if (library.id == activeLibraryId) {
                        activeLibraryChangedCount += result.inserted + result.updated + result.deleted
                    }
                    Timber.d(
                        "StartupTrace: sync library end id=${library.id} inserted=${result.inserted} updated=${result.updated} deleted=${result.deleted}"
                    )
                    Timber.d(
                        "LibrarySyncManager: library sync done name=%s inserted=%s updated=%s deleted=%s",
                        library.name,
                        result.inserted,
                        result.updated,
                        result.deleted
                    )
                }
                is SyncResult.Failure -> {
                    Timber.d("StartupTrace: sync library failed id=${library.id} name=${library.name}")
                    logSyncFailure("LibrarySyncManager: library sync failed name=${library.name}", result.error)
                }
            }
            if (reindexVisible) {
                eventBus.emit(AppEvent.SyncCompleted(library.id))
            }
        }

        return activeLibraryChangedCount
    }

    private fun logSyncFailure(message: String, failure: SyncFailure) {
        when (failure) {
            is SyncFailure.PermissionLost -> Timber.e(failure.cause, "%s reason=PermissionLost folder=%s", message, failure.folderUri)
            is SyncFailure.FolderUnavailable -> Timber.e(failure.cause, "%s reason=FolderUnavailable folder=%s detail=%s", message, failure.folderUri, failure.reason)
            is SyncFailure.UnknownFailure -> Timber.e(failure.cause, "%s reason=UnknownFailure", message)
        }
    }

    private companion object {
        /**
         * 待重建索引的文件数达到该阈值才广播进度事件（显示"数据库更新中"进度页）
         *
         * 数据库升级回填通常是全库量级；库很小的场景重建本身瞬间完成，无需提示
         */
        const val REINDEX_PROGRESS_MIN_TOTAL = 20

        /** 重建索引进度事件的节流间隔（首尾必发） */
        const val REINDEX_PROGRESS_EVERY = 5
    }
}
