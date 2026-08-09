package com.bird.fiber.data.local

import android.content.ContentResolver
import android.net.Uri
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownFileEntity
import com.bird.fiber.data.local.library.MarkdownIndexSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class FileIndexerTest {

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private val markdownFileDao = mockk<MarkdownFileDao>(relaxed = true)
    private val scanner = mockk<MarkdownFileScanner>()
    private val contentReader = mockk<MarkdownContentReader>()
    private val previewReader = mockk<MarkdownPreviewReader>()
    private val syncPlanner = MarkdownSyncPlanner()
    private val writer = mockk<MarkdownIndexWriter>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    private val fileIndexer = FileIndexer(
        markdownFileDao = markdownFileDao,
        scanner = scanner,
        contentReader = contentReader,
        previewReader = previewReader,
        syncPlanner = syncPlanner,
        writer = writer
    )

    @Test
    fun syncLibrary_scannerFailure_returnsFailure() = runTest {
        val error = IllegalStateException("boom")
        coEvery {
            scanner.scan(contentResolver, "folder-uri", "library-1")
        } throws error

        val result = fileIndexer.syncLibrary(
            contentResolver = contentResolver,
            libraryId = "library-1",
            folderUri = "folder-uri"
        )

        require(result is SyncResult.Failure)
        require(result.error is SyncFailure.UnknownFailure)
        assertSame(error, result.error.cause)
    }

    @Test
    fun updateFileAfterSave_usesPreviewReaderAndUpdatesEntity() = runTest {
        val updatedEntity = slot<MarkdownFileEntity>()

        val existing = entityFile(
            uri = "file-uri",
            preview = "old-preview",
            lastModified = 100L,
            size = 10L
        )
        coEvery { markdownFileDao.getFileByUri("file-uri") } returns existing
        every { previewReader.readFromContent("# title\nbody") } returns "new-preview"
        coEvery { writer.update(capture(updatedEntity)) } returns Unit

        fileIndexer.updateFileAfterSave(
            fileUri = "file-uri",
            content = "# title\nbody"
        )

        verify(exactly = 1) { previewReader.readFromContent("# title\nbody") }
        coVerify(exactly = 1) { writer.update(any()) }
        assertEquals("file-uri", updatedEntity.captured.uri)
        assertEquals("new-preview", updatedEntity.captured.contentPreview)
        assertEquals("# title\nbody", updatedEntity.captured.contentText)
        assertEquals("# title\nbody".toByteArray().size.toLong(), updatedEntity.captured.size)
    }

    @Test
    fun updateFileAfterSave_usesProvidedFileSizeWithoutRecomputingBytes() = runTest {
        val updatedEntity = slot<MarkdownFileEntity>()
        val existing = entityFile(uri = "file-uri", preview = "old-preview", lastModified = 100L, size = 10L)
        coEvery { markdownFileDao.getFileByUri("file-uri") } returns existing
        every { previewReader.readFromContent("正文") } returns "preview"
        coEvery { writer.update(capture(updatedEntity)) } returns Unit

        fileIndexer.updateFileAfterSave("file-uri", "正文", size = 2048L)

        assertEquals(2048L, updatedEntity.captured.size)
    }

    @Test
    fun syncLibrary_newAndDeletedFiles_writesExpectedEntities() = runTest {
        var capturedDeletedUris: List<String>? = null
        var capturedUpsertFiles: List<MarkdownFileEntity>? = null

        mockUriParse("new-uri")

        val scannedFiles = listOf(
            scannedFile(uri = "new-uri", lastModified = 300L, size = 30L),
            scannedFile(uri = "same-uri", lastModified = 100L, size = 10L)
        )
        val cachedFiles = listOf(
            snapshotFile(uri = "same-uri", lastModified = 100L),
            snapshotFile(uri = "deleted-uri", lastModified = 50L)
        )

        every {
            scanner.scan(contentResolver, "folder-uri", "library-1")
        } returns scannedFiles
        coEvery { markdownFileDao.getIndexSnapshotsByLibrary("library-1") } returns cachedFiles
        every { contentReader.read(contentResolver, Uri.parse("new-uri")) } returns "# new"
        every { previewReader.readFromContent("# new") } returns "new-preview"
        coEvery { writer.upsertBatch(any()) } answers {
            capturedUpsertFiles = arg(0)
            Unit
        }
        coEvery { writer.deleteMissing(any()) } answers {
            capturedDeletedUris = arg(0)
            Unit
        }

        val result = fileIndexer.syncLibrary(
            contentResolver = contentResolver,
            libraryId = "library-1",
            folderUri = "folder-uri"
        )

        require(result is SyncResult.Success)
        assertEquals(1, result.inserted)
        assertEquals(0, result.updated)
        assertEquals(1, result.deleted)

        coVerify(exactly = 1) { writer.upsertBatch(any()) }
        coVerify(exactly = 1) { writer.deleteMissing(any()) }
        assertEquals(listOf("deleted-uri"), capturedDeletedUris)
        assertEquals(1, capturedUpsertFiles?.size)
        assertEquals("new-uri", capturedUpsertFiles?.single()?.uri)
        assertEquals("new-preview", capturedUpsertFiles?.single()?.contentPreview)
        assertEquals("# new", capturedUpsertFiles?.single()?.contentText)
    }

    @Test
    fun syncLibrary_missingPreview_marksUpdateAndReportsProgress() = runTest {
        var capturedDeletedUris: List<String>? = null
        var capturedUpsertFiles: List<MarkdownFileEntity>? = null

        mockUriParse("uri-1")

        val progress = mutableListOf<Pair<Int, Int>>()
        val scannedFiles = listOf(
            scannedFile(uri = "uri-1", lastModified = 100L, size = 10L)
        )
        val cachedFiles = listOf(
            snapshotFile(uri = "uri-1", lastModified = 100L, hasPreview = false)
        )

        every {
            scanner.scan(contentResolver, "folder-uri", "library-1")
        } returns scannedFiles
        coEvery { markdownFileDao.getIndexSnapshotsByLibrary("library-1") } returns cachedFiles
        every { contentReader.read(contentResolver, Uri.parse("uri-1")) } returns "# title"
        every { previewReader.readFromContent("# title") } returns "filled-preview"
        coEvery { writer.upsertBatch(any()) } answers {
            capturedUpsertFiles = arg(0)
            Unit
        }
        coEvery { writer.deleteMissing(any()) } answers {
            capturedDeletedUris = arg(0)
            Unit
        }

        val result = fileIndexer.syncLibrary(
            contentResolver = contentResolver,
            libraryId = "library-1",
            folderUri = "folder-uri",
            onProgress = { current, total -> progress += current to total }
        )

        require(result is SyncResult.Success)
        assertEquals(0, result.inserted)
        assertEquals(1, result.updated)
        assertEquals(0, result.deleted)
        assertEquals(listOf(1 to 1), progress)

        coVerify(exactly = 1) { writer.upsertBatch(any()) }
        coVerify(exactly = 1) { writer.deleteMissing(any()) }
        assertEquals(emptyList<String>(), capturedDeletedUris)
        assertEquals(1, capturedUpsertFiles?.size)
        assertEquals("filled-preview", capturedUpsertFiles?.single()?.contentPreview)
        assertEquals("# title", capturedUpsertFiles?.single()?.contentText)
    }

    @Test
    fun syncLibrary_permissionLost_returnsTypedFailure() = runTest {
        val error = SecurityException("denied")
        coEvery { scanner.scan(contentResolver, "folder-uri", "library-1") } throws error

        val result = fileIndexer.syncLibrary(
            contentResolver = contentResolver,
            libraryId = "library-1",
            folderUri = "folder-uri"
        )

        require(result is SyncResult.Failure)
        require(result.error is SyncFailure.PermissionLost)
        assertEquals("folder-uri", result.error.folderUri)
        assertSame(error, result.error.cause)
    }

    @Test
    fun syncLibrary_massDeletionGuard_returnsFolderUnavailableAndSkipsWrite() = runTest {
        val scannedFiles = emptyList<ScannedFile>()
        val cachedFiles = (1..10).map { index ->
            snapshotFile(uri = "uri-$index", lastModified = index.toLong())
        }

        coEvery { scanner.scan(contentResolver, "folder-uri", "library-1") } returns scannedFiles
        coEvery { markdownFileDao.getIndexSnapshotsByLibrary("library-1") } returns cachedFiles

        val result = fileIndexer.syncLibrary(
            contentResolver = contentResolver,
            libraryId = "library-1",
            folderUri = "folder-uri"
        )

        require(result is SyncResult.Failure)
        require(result.error is SyncFailure.FolderUnavailable)
        assertEquals("folder-uri", result.error.folderUri)
        coVerify(exactly = 0) { writer.upsertBatch(any()) }
        coVerify(exactly = 0) { writer.deleteMissing(any()) }
    }

    @Test
    fun syncLibrary_manyChangedFiles_writesBoundedBatches() = runTest {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        val scannedFiles = (1..120).map { index ->
            scannedFile(uri = "uri-$index", lastModified = index.toLong(), size = 10L)
        }
        every { scanner.scan(contentResolver, "folder-uri", "library-1") } returns scannedFiles
        coEvery { markdownFileDao.getIndexSnapshotsByLibrary("library-1") } returns emptyList()
        every { contentReader.read(contentResolver, any()) } returns "content"
        every { previewReader.readFromContent("content") } returns "preview"
        val batchSizes = mutableListOf<Int>()
        coEvery { writer.upsertBatch(any()) } answers {
            batchSizes += arg<List<MarkdownFileEntity>>(0).size
        }

        val result = fileIndexer.syncLibrary(
            contentResolver = contentResolver,
            libraryId = "library-1",
            folderUri = "folder-uri"
        )

        require(result is SyncResult.Success)
        assertEquals(listOf(50, 50, 20), batchSizes)
        assertEquals(120, result.inserted)
    }

    @Test
    fun syncLibrary_sameAndDifferentLibrariesRemainGloballySerialAndRecordMetrics() = runTest {
        val metrics = CopyOnWriteArrayList<IndexLockMetric>()
        val indexer = FileIndexer(
            markdownFileDao = markdownFileDao,
            scanner = scanner,
            contentReader = contentReader,
            previewReader = previewReader,
            syncPlanner = syncPlanner,
            writer = writer,
            lockMetrics = IndexLockMetrics(metrics::add)
        )
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        every { scanner.scan(contentResolver, "folder-a", "library-a") } answers {
            firstStarted.countDown()
            releaseFirst.await(5, TimeUnit.SECONDS)
            emptyList()
        }
        every { scanner.scan(contentResolver, "folder-b", "library-b") } answers {
            secondStarted.countDown()
            emptyList()
        }
        coEvery { markdownFileDao.getIndexSnapshotsByLibrary(any()) } returns emptyList()

        val first = launch {
            indexer.syncLibrary(contentResolver, "library-a", "folder-a", reason = "active-library")
        }
        runCurrent()
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS))

        val second = launch {
            indexer.syncLibrary(contentResolver, "library-b", "folder-b", reason = "inactive-libraries")
        }
        runCurrent()
        assertFalse(secondStarted.await(150, TimeUnit.MILLISECONDS))

        releaseFirst.countDown()
        first.join()
        second.join()
        assertTrue(secondStarted.await(2, TimeUnit.SECONDS))
        assertEquals(setOf("active-library", "inactive-libraries"), metrics.map { it.reason }.toSet())
        assertTrue(metrics.all { it.acquired && it.holdMillis >= 0L })
        assertTrue(metrics.single { it.reason == "inactive-libraries" }.waitMillis >= 100L)
    }

    @Test
    fun syncLibrary_twoTasksForSameLibraryDoNotOverlap() = runTest {
        val indexer = FileIndexer(
            markdownFileDao = markdownFileDao,
            scanner = scanner,
            contentReader = contentReader,
            previewReader = previewReader,
            syncPlanner = syncPlanner,
            writer = writer
        )
        val callCount = AtomicInteger(0)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        every { scanner.scan(contentResolver, "folder-a", "library-a") } answers {
            if (callCount.incrementAndGet() == 1) {
                firstStarted.countDown()
                releaseFirst.await(5, TimeUnit.SECONDS)
            } else {
                secondStarted.countDown()
            }
            emptyList()
        }
        coEvery { markdownFileDao.getIndexSnapshotsByLibrary("library-a") } returns emptyList()

        val first = launch { indexer.syncLibrary(contentResolver, "library-a", "folder-a", reason = "same-1") }
        runCurrent()
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
        val second = launch { indexer.syncLibrary(contentResolver, "library-a", "folder-a", reason = "same-2") }
        runCurrent()
        assertFalse(secondStarted.await(150, TimeUnit.MILLISECONDS))

        releaseFirst.countDown()
        first.join()
        second.join()
        assertTrue(secondStarted.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun syncLibrary_cancelledWhileWaitingDoesNotEnterProviderOrLeakLock() = runTest {
        val metrics = CopyOnWriteArrayList<IndexLockMetric>()
        val indexer = FileIndexer(
            markdownFileDao = markdownFileDao,
            scanner = scanner,
            contentReader = contentReader,
            previewReader = previewReader,
            syncPlanner = syncPlanner,
            writer = writer,
            lockMetrics = IndexLockMetrics(metrics::add)
        )
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        every { scanner.scan(contentResolver, "folder-a", "library-a") } answers {
            firstStarted.countDown()
            releaseFirst.await(5, TimeUnit.SECONDS)
            emptyList()
        }
        coEvery { markdownFileDao.getIndexSnapshotsByLibrary(any()) } returns emptyList()

        val first = launch {
            indexer.syncLibrary(contentResolver, "library-a", "folder-a", reason = "active-library")
        }
        runCurrent()
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
        val cancelled = launch {
            indexer.syncLibrary(contentResolver, "library-b", "folder-b", reason = "cancelled-sync")
        }
        runCurrent()
        Thread.sleep(100)
        cancelled.cancelAndJoin()
        releaseFirst.countDown()
        first.join()

        verify(exactly = 0) { scanner.scan(contentResolver, "folder-b", "library-b") }
        val cancelledMetric = metrics.single { it.reason == "cancelled-sync" }
        assertFalse(cancelledMetric.acquired)

        every { scanner.scan(contentResolver, "folder-c", "library-c") } returns emptyList()
        indexer.syncLibrary(contentResolver, "library-c", "folder-c", reason = "after-cancel")
        assertTrue(metrics.any { it.reason == "after-cancel" && it.acquired })
    }

    private fun entityFile(
        uri: String,
        preview: String,
        lastModified: Long,
        size: Long,
        contentText: String = "content"
    ) = MarkdownFileEntity(
        uri = uri,
        name = "name",
        path = "name.md",
        lastModified = lastModified,
        size = size,
        libraryId = "library-1",
        contentPreview = preview,
        contentText = contentText,
        isDeleted = 0
    )

    private fun scannedFile(
        uri: String,
        lastModified: Long,
        size: Long
    ) = ScannedFile(
        uri = uri,
        name = "name",
        path = "name.md",
        lastModified = lastModified,
        size = size,
        libraryId = "library-1"
    )

    private fun snapshotFile(
        uri: String,
        lastModified: Long,
        hasPreview: Boolean = true,
        hasSearchContent: Boolean = true
    ) = MarkdownIndexSnapshot(
        uri = uri,
        lastModified = lastModified,
        hasPreview = hasPreview,
        hasSearchContent = hasSearchContent
    )

    private fun mockUriParse(uriString: String) {
        mockkStatic(Uri::class)
        every { Uri.parse(uriString) } returns mockk(relaxed = true)
    }
}
