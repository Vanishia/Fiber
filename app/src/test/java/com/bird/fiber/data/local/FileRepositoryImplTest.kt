package com.bird.fiber.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.LibraryTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class FileRepositoryImplTest {

    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val libraryRepository = mockk<LibraryRepository>()
    private val fileIndexer = mockk<FileIndexer>(relaxed = true)
    private lateinit var repository: FileRepositoryImpl

    private val targetA = LibraryTarget("library-a", "content://tree/library-a")
    private val activeLibraryB = LibraryEntity(
        id = "library-b",
        name = "B",
        folderUri = "content://tree/library-b",
        createdAt = 0L,
        isActive = true
    )

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        mockkStatic(DocumentsContract::class)
        every { context.contentResolver } returns contentResolver
        every { libraryRepository.getActiveLibrary() } returns flowOf(activeLibraryB)
        repository = FileRepositoryImpl(context, libraryRepository, fileIndexer)
    }

    @After
    fun tearDown() {
        unmockkStatic(DocumentsContract::class)
        unmockkStatic(Uri::class)
    }

    @Test
    fun createMarkdownFile_usesExplicitTargetForIndexWhenActiveLibraryDiffers() = runTest {
        val (newFileUri, newFileUriString) = prepareDocumentCreation(output = ByteArrayOutputStream())
        coEvery {
            fileIndexer.insertFile(contentResolver, targetA.libraryId, targetA.folderUri, newFileUriString)
        } returns true

        val result = repository.createMarkdownFile(targetA, "note", "content")

        assertTrue(result.toString(), result is FileResult.Success)
        coVerify(exactly = 1) {
            fileIndexer.insertFile(contentResolver, "library-a", targetA.folderUri, newFileUriString)
        }
        coVerify(exactly = 0) {
            fileIndexer.insertFile(contentResolver, "library-b", any(), any())
        }
    }

    @Test
    fun createMarkdownFile_writeFailure_doesNotInsertIndexAndDeletesDocument() = runTest {
        val (newFileUri, newFileUriString) = prepareDocumentCreation(output = null)

        val result = repository.createMarkdownFile(targetA, "note", "content")

        assertTrue(result is FileResult.Error)
        coVerify(exactly = 0) { fileIndexer.insertFile(any(), any(), any(), any()) }
        coVerify(exactly = 1) { fileIndexer.deleteFile(newFileUriString) }
        verify(exactly = 1) { DocumentsContract.deleteDocument(contentResolver, newFileUri) }
    }

    @Test
    fun createMarkdownFile_indexFailure_rollsBackFileAndIndex() = runTest {
        val (newFileUri, newFileUriString) = prepareDocumentCreation(output = ByteArrayOutputStream())
        coEvery { fileIndexer.insertFile(any(), any(), any(), any()) } returns false

        val result = repository.createMarkdownFile(targetA, "note", "content")

        assertTrue(result is FileResult.Error)
        coVerify(exactly = 1) { fileIndexer.deleteFile(newFileUriString) }
        verify(exactly = 1) { DocumentsContract.deleteDocument(contentResolver, newFileUri) }
    }

    private fun prepareDocumentCreation(output: ByteArrayOutputStream?): Pair<Uri, String> {
        val treeUri = mockk<Uri>()
        val documentUri = mockk<Uri>()
        val newFileUri = mockk<Uri>()
        val newFileUriString = "content://documents/library-a/note.md"
        every { newFileUri.toString() } returns newFileUriString
        every { Uri.parse(targetA.folderUri) } returns treeUri
        every { DocumentsContract.getTreeDocumentId(treeUri) } returns "library-a"
        every { DocumentsContract.buildDocumentUriUsingTree(treeUri, "library-a") } returns documentUri
        every {
            DocumentsContract.createDocument(contentResolver, documentUri, "text/markdown", "note.md")
        } returns newFileUri
        every { contentResolver.openOutputStream(newFileUri, "wt") } returns output
        return newFileUri to newFileUriString
    }
}
