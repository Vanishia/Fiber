package com.bird.fiber.domain.sync

import android.content.ContentResolver
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.local.FileIndexer
import com.bird.fiber.data.local.SyncResult
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

class LibrarySyncManagerTest {

    private val libraryRepository = mockk<LibraryRepository>(relaxed = true)
    private val eventBus = mockk<EventBus>(relaxed = true)
    private val fileIndexer = mockk<FileIndexer>()
    private val contentResolver = mockk<ContentResolver>()

    private val manager = LibrarySyncManager(
        libraryRepository = libraryRepository,
        eventBus = eventBus,
        fileIndexer = fileIndexer
    )

    @Test
    fun addLibraryAndSync_existingFolder_reusesLibraryAndReportsProgress() = runTest {
        val existing = LibraryEntity(
            id = "library-1",
            name = "已有库",
            folderUri = "content://folder",
            createdAt = 1000,
            lastOpenedAt = 2000,
            isActive = false
        )
        coEvery { libraryRepository.getLibraryByFolderUri("content://folder") } returns existing
        coEvery {
            fileIndexer.syncLibrary(
                contentResolver = contentResolver,
                libraryId = "library-1",
                folderUri = "content://folder",
                reason = "existing-library",
                onProgress = any()
            )
        } answers {
            arg<((Int, Int) -> Unit)>(4).invoke(0, 3)
            arg<((Int, Int) -> Unit)>(4).invoke(1, 3)
            SyncResult.Success(inserted = 3, updated = 0, deleted = 0)
        }
        every { eventBus.tryEmit(any()) } returns true

        val result = manager.addLibraryAndSync(
            contentResolver = contentResolver,
            folderName = "重复选择的名字",
            folderUriString = "content://folder"
        )

        assertSame(existing, result)
        coVerify(exactly = 0) { libraryRepository.addLibrary(any()) }
        coVerify { libraryRepository.switchLibrary("library-1") }
        verify { eventBus.tryEmit(AppEvent.SyncProgress("library-1", 0, 3)) }
        verify { eventBus.tryEmit(AppEvent.SyncProgress("library-1", 1, 3)) }
        coVerify { eventBus.emit(AppEvent.SyncStarted("library-1")) }
        coVerify { eventBus.emit(AppEvent.SyncCompleted("library-1")) }
    }
}
