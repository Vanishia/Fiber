package com.bird.fiber.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownImageNoteContent
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.ManagedAttachment
import io.mockk.coEvery
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.LocalDateTime

class AttachmentRepositoryImplTest {

    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val libraryRepository = mockk<LibraryRepository>()
    private val markdownFileDao = mockk<MarkdownFileDao>()
    private lateinit var repository: AttachmentRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        mockkStatic(DocumentsContract::class)
        every { context.contentResolver } returns contentResolver
        repository = AttachmentRepositoryImpl(context, libraryRepository, markdownFileDao)
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
    fun loadReferences_matchesAgainstIndexedNoteContents() = runTest {
        val attachment = ManagedAttachment(
            displayName = "a.png",
            relativePath = "attachments/a.png",
            uri = "content://test/attachments/a.png",
            mimeType = "image/png",
            size = 1L,
            lastModified = 1L,
            width = 1,
            height = 1,
            referencedBy = emptyList()
        )
        coEvery { markdownFileDao.getImageNoteContentsByLibrary("lib") } returns listOf(
            MarkdownImageNoteContent(
                uri = "content://test/note-1",
                name = "note-1",
                contentText = "![图片](<attachments/a.png>)"
            ),
            MarkdownImageNoteContent(
                uri = "content://test/note-2",
                name = "note-2",
                contentText = "没有图片的正文"
            )
        )

        val result = repository.loadReferencesForLibrary("lib", listOf(attachment))

        require(result is FileResult.Success)
        assertEquals(
            listOf("content://test/note-1"),
            result.data.getValue(attachment.uri).map { it.fileUri }
        )
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

    @Test
    fun normalizeAttachmentPath_acceptsRelativeAndEncodedAttachmentPaths() {
        assertEquals(
            "attachments/image one.png",
            normalizeAttachmentPath("./attachments/image%20one.png")
        )
        assertNull(normalizeAttachmentPath("../attachments/image.png"))
        assertNull(normalizeAttachmentPath("https://example.com/image.png"))
    }

    @Test
    fun findAttachmentReferences_supportsParsedAndLegacySpacePaths() {
        val relativePath = "attachments/image one.png"
        val parsed = MarkdownAttachmentSource(
            fileUri = "content://test/parsed",
            fileName = "parsed",
            content = "![图片](<attachments/image one.png>)",
            destinations = setOf(relativePath)
        )
        val legacy = MarkdownAttachmentSource(
            fileUri = "content://test/legacy",
            fileName = "legacy",
            content = "![图片](attachments/image one.png)",
            destinations = emptySet()
        )

        val references = findAttachmentReferences(relativePath, listOf(parsed, legacy))

        assertEquals(listOf("parsed", "legacy"), references.map { it.fileName })
    }
}
