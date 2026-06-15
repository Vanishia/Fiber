package com.bird.fiber.data.local

import android.content.ContentResolver
import android.net.Uri
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownFileEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.After
import org.junit.Test

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
    fun syncLibrary_newAndDeletedFiles_writesExpectedEntities() = runTest {
        var capturedDeletedUris: List<String>? = null
        var capturedUpsertFiles: List<MarkdownFileEntity>? = null

        mockUriParse("new-uri")

        val scannedFiles = listOf(
            scannedFile(uri = "new-uri", lastModified = 300L, size = 30L),
            scannedFile(uri = "same-uri", lastModified = 100L, size = 10L)
        )
        val cachedFiles = listOf(
            entityFile(uri = "same-uri", preview = "ready", lastModified = 100L, size = 10L),
            entityFile(uri = "deleted-uri", preview = "old", lastModified = 50L, size = 5L)
        )

        every {
            scanner.scan(contentResolver, "folder-uri", "library-1")
        } returns scannedFiles
        coEvery { markdownFileDao.getAllByLibrary("library-1") } returns cachedFiles
        every { contentReader.read(contentResolver, Uri.parse("new-uri")) } returns "# new"
        every { previewReader.readFromContent("# new") } returns "new-preview"
        coEvery { writer.applySync(any(), any()) } answers {
            capturedDeletedUris = arg(0)
            capturedUpsertFiles = arg(1)
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

        coVerify(exactly = 1) { writer.applySync(any(), any()) }
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
            entityFile(uri = "uri-1", preview = "", lastModified = 100L, size = 10L)
        )

        every {
            scanner.scan(contentResolver, "folder-uri", "library-1")
        } returns scannedFiles
        coEvery { markdownFileDao.getAllByLibrary("library-1") } returns cachedFiles
        every { contentReader.read(contentResolver, Uri.parse("uri-1")) } returns "# title"
        every { previewReader.readFromContent("# title") } returns "filled-preview"
        coEvery { writer.applySync(any(), any()) } answers {
            capturedDeletedUris = arg(0)
            capturedUpsertFiles = arg(1)
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

        coVerify(exactly = 1) { writer.applySync(any(), any()) }
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
            entityFile(uri = "uri-$index", preview = "old", lastModified = index.toLong(), size = 10L)
        }

        coEvery { scanner.scan(contentResolver, "folder-uri", "library-1") } returns scannedFiles
        coEvery { markdownFileDao.getAllByLibrary("library-1") } returns cachedFiles

        val result = fileIndexer.syncLibrary(
            contentResolver = contentResolver,
            libraryId = "library-1",
            folderUri = "folder-uri"
        )

        require(result is SyncResult.Failure)
        require(result.error is SyncFailure.FolderUnavailable)
        assertEquals("folder-uri", result.error.folderUri)
        coVerify(exactly = 0) { writer.applySync(any(), any()) }
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

    private fun mockUriParse(uriString: String) {
        mockkStatic(Uri::class)
        every { Uri.parse(uriString) } returns mockk(relaxed = true)
    }
}
