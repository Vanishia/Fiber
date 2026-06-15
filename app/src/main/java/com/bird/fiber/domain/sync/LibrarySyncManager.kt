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
        val newLibrary = LibraryEntity(
            id = UUID.randomUUID().toString(),
            name = folderName,
            folderUri = folderUriString,
            createdAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis(),
            isActive = false
        )

        libraryRepository.addLibrary(newLibrary)
        libraryRepository.switchLibrary(newLibrary.id)

        eventBus.emit(AppEvent.SyncStarted(newLibrary.id))

        withContext(Dispatchers.IO) {
            Timber.d("LibrarySyncManager: start sync new library=%s", newLibrary.name)
            when (val result = fileIndexer.syncLibrary(
                contentResolver = contentResolver,
                libraryId = newLibrary.id,
                folderUri = folderUriString
            )) {
                is SyncResult.Success -> {
                    Timber.d(
                        "LibrarySyncManager: new library sync done name=%s inserted=%s updated=%s deleted=%s",
                        newLibrary.name,
                        result.inserted,
                        result.updated,
                        result.deleted
                    )
                }
                is SyncResult.Failure -> {
                    logSyncFailure("LibrarySyncManager: new library sync failed name=${newLibrary.name}", result.error)
                }
            }
        }

        eventBus.emit(AppEvent.SyncCompleted(newLibrary.id))
        eventBus.emit(AppEvent.RefreshFileList)

        return newLibrary
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

    private suspend fun syncLibraries(
        contentResolver: ContentResolver,
        libraries: List<LibraryEntity>,
        activeLibraryId: String?,
        onProgress: ((libraryName: String, current: Int, total: Int) -> Unit)?
    ): Int {
        var activeLibraryChangedCount = 0

        libraries.forEach { library ->
            val folderUri = library.folderUri
            Timber.d("StartupTrace: sync library begin id=${library.id} name=${library.name}")
            when (val result = fileIndexer.syncLibrary(
                contentResolver = contentResolver,
                libraryId = library.id,
                folderUri = folderUri,
                onProgress = { current, total ->
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
}
