package com.bird.fiber.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.model.FileResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class AttachmentRepositoryImplTest {

    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val libraryRepository = mockk<LibraryRepository>()
    private lateinit var repository: AttachmentRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        mockkStatic(DocumentsContract::class)
        every { context.contentResolver } returns contentResolver
        repository = AttachmentRepositoryImpl(context, libraryRepository)
    }

    @After
    fun tearDown() {
        unmockkStatic(DocumentsContract::class)
        unmockkStatic(Uri::class)
    }

    @Test
    fun delete_deletesOnlyExactUri() = runTest {
        val uriString = "content://documents/library/attachments/image.png"
        val uri = mockk<Uri>()
        every { Uri.parse(uriString) } returns uri
        every { DocumentsContract.deleteDocument(contentResolver, uri) } returns true

        val result = repository.delete(uriString)

        assertTrue(result is FileResult.Success)
        verify(exactly = 1) { DocumentsContract.deleteDocument(contentResolver, uri) }
    }

    @Test
    fun delete_providerReturnsFalse_returnsError() = runTest {
        val uriString = "content://documents/library/attachments/image.png"
        val uri = mockk<Uri>()
        every { Uri.parse(uriString) } returns uri
        every { DocumentsContract.deleteDocument(contentResolver, uri) } returns false

        val result = repository.delete(uriString)

        assertTrue(result is FileResult.Error)
    }

    @Test
    fun delete_permissionDenied_returnsTypedError() = runTest {
        val uriString = "content://documents/library/attachments/image.png"
        val uri = mockk<Uri>()
        every { Uri.parse(uriString) } returns uri
        every { DocumentsContract.deleteDocument(contentResolver, uri) } throws SecurityException("denied")

        val result = repository.delete(uriString)

        require(result is FileResult.Error)
        assertTrue(result.error is com.bird.fiber.data.model.FileError.PermissionDenied)
    }

    @Test
    fun fileName_preservesOriginalNameAndAddsTimestampAndSuffix() {
        val fileName = AttachmentFileNameGenerator.generate(
            displayName = "diagram.png",
            extension = "png",
            timestamp = LocalDateTime.of(2026, 8, 9, 15, 30, 12),
            suffix = "a1b2"
        )

        assertTrue(fileName == "diagram-20260809-153012-a1b2.png")
    }
}
