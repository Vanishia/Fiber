package com.bird.fiber.ui.screens.attachments

import androidx.lifecycle.SavedStateHandle
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.model.AttachmentDeletionSummary
import com.bird.fiber.data.model.AttachmentReference
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.ManagedAttachment
import com.bird.fiber.data.repository.AttachmentRepository
import com.bird.fiber.data.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentManagerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var attachmentRepository: AttachmentRepository
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var fileRepository: FileRepository
    private lateinit var markdownFileDao: MarkdownFileDao

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        attachmentRepository = mockk()
        libraryRepository = mockk()
        fileRepository = mockk()
        markdownFileDao = mockk()
        coEvery { libraryRepository.getLibraryById(LIBRARY_ID) } returns LIBRARY
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun filter_showsOnlyRequestedAssociationState() {
        coEvery { attachmentRepository.listForLibrary(LIBRARY_ID) } returns
            FileResult.Success(QUICK_ATTACHMENTS)
        coEvery {
            attachmentRepository.loadReferencesForLibrary(LIBRARY_ID, any())
        } returns FileResult.Success(REFERENCE_MAP)
        val viewModel = createViewModel()

        viewModel.setFilter(AttachmentFilter.REFERENCED)
        assertEquals(listOf(REFERENCED), viewModel.uiState.value.filteredAttachments)

        viewModel.setFilter(AttachmentFilter.ORPHANED)
        assertEquals(listOf(ORPHAN), viewModel.uiState.value.filteredAttachments)
    }

    @Test
    fun longPress_selectsOnlyOrphanAttachments() {
        coEvery { attachmentRepository.listForLibrary(LIBRARY_ID) } returns
            FileResult.Success(QUICK_ATTACHMENTS)
        coEvery {
            attachmentRepository.loadReferencesForLibrary(LIBRARY_ID, any())
        } returns FileResult.Success(REFERENCE_MAP)
        val viewModel = createViewModel()

        viewModel.startSelection(REFERENCED)
        assertTrue(viewModel.uiState.value.selectedUris.isEmpty())
        assertEquals("暂不支持删除有关联的文件", viewModel.uiState.value.message)

        viewModel.startSelection(ORPHAN)
        assertEquals(setOf(ORPHAN.uri), viewModel.uiState.value.selectedUris)
    }

    @Test
    fun deleteSelected_usesSafeOrphanDeletionAndReloads() = runTest {
        coEvery { attachmentRepository.listForLibrary(LIBRARY_ID) } returnsMany listOf(
            FileResult.Success(listOf(ORPHAN)),
            FileResult.Success(emptyList())
        )
        coEvery {
            attachmentRepository.loadReferencesForLibrary(LIBRARY_ID, any())
        } returns FileResult.Success(emptyMap())
        coEvery {
            attachmentRepository.deleteOrphans(LIBRARY_ID, setOf(ORPHAN.uri))
        } returns FileResult.Success(AttachmentDeletionSummary(1, 0, 0))
        val viewModel = createViewModel()
        viewModel.startSelection(ORPHAN)

        viewModel.deleteSelected()

        coVerify(exactly = 1) {
            attachmentRepository.deleteOrphans(LIBRARY_ID, setOf(ORPHAN.uri))
        }
        assertFalse(viewModel.uiState.value.isSelecting)
        assertTrue(viewModel.uiState.value.attachments.isEmpty())
    }

    @Test
    fun attachments_areVisibleBeforeReferencesFinishLoading() = runTest {
        val references = CompletableDeferred<FileResult<Map<String, List<AttachmentReference>>>>()
        coEvery { attachmentRepository.listForLibrary(LIBRARY_ID) } returns
            FileResult.Success(QUICK_ATTACHMENTS)
        coEvery {
            attachmentRepository.loadReferencesForLibrary(LIBRARY_ID, any())
        } coAnswers { references.await() }

        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isReferencesLoading)
        assertFalse(viewModel.uiState.value.referencesLoaded)
        assertEquals(2, viewModel.uiState.value.attachments.size)
        assertTrue(viewModel.uiState.value.attachments.all { it.referencedBy.isEmpty() })

        viewModel.setFilter(AttachmentFilter.REFERENCED)
        assertEquals(AttachmentFilter.ALL, viewModel.uiState.value.filter)

        references.complete(FileResult.Success(REFERENCE_MAP))

        assertTrue(viewModel.uiState.value.referencesLoaded)
        assertEquals(REFERENCED.referencedBy, viewModel.uiState.value.attachments[1].referencedBy)
    }

    @Test
    fun openLinkedNotes_singleReference_opensNoteDirectly() = runTest {
        coEvery { attachmentRepository.listForLibrary(LIBRARY_ID) } returns
            FileResult.Success(QUICK_ATTACHMENTS)
        coEvery {
            attachmentRepository.loadReferencesForLibrary(LIBRARY_ID, any())
        } returns FileResult.Success(REFERENCE_MAP)
        coEvery { markdownFileDao.getFileByUri("content://test/note") } returns null
        coEvery { fileRepository.readFileContent("content://test/note") } returns
            FileResult.Success("笔记全文")
        val viewModel = createViewModel()

        viewModel.openLinkedNotes(REFERENCED)

        val state = viewModel.uiState.value
        assertEquals(null, state.linkedNoteChoices)
        assertEquals("content://test/note", state.viewingLinkedNote?.fileUri)
        assertEquals("笔记全文", state.viewingLinkedNote?.content)
    }

    @Test
    fun openLinkedNotes_multipleReferences_showsChoicesThenSelectedNote() = runTest {
        val references = listOf(
            AttachmentReference("content://test/note-a", "note-a"),
            AttachmentReference("content://test/note-b", "note-b")
        )
        val multiReferenced = REFERENCED.copy(referencedBy = references)
        coEvery { attachmentRepository.listForLibrary(LIBRARY_ID) } returns
            FileResult.Success(listOf(multiReferenced))
        coEvery {
            attachmentRepository.loadReferencesForLibrary(LIBRARY_ID, any())
        } returns FileResult.Success(mapOf(multiReferenced.uri to references))
        coEvery { markdownFileDao.getFileByUri(any()) } returns null
        coEvery { fileRepository.readFileContent(any()) } returns FileResult.Success("内容")
        val viewModel = createViewModel()

        viewModel.openLinkedNotes(multiReferenced)

        val choices = viewModel.uiState.value.linkedNoteChoices
        assertEquals(2, choices?.size)
        assertEquals(null, viewModel.uiState.value.viewingLinkedNote)

        viewModel.openLinkedNote(choices!!.first())

        val state = viewModel.uiState.value
        assertEquals(null, state.linkedNoteChoices)
        assertEquals("content://test/note-a", state.viewingLinkedNote?.fileUri)
    }

    private fun createViewModel() = AttachmentManagerViewModel(
        attachmentRepository = attachmentRepository,
        libraryRepository = libraryRepository,
        fileRepository = fileRepository,
        markdownFileDao = markdownFileDao,
        savedStateHandle = SavedStateHandle(mapOf("libraryId" to LIBRARY_ID))
    )

    private companion object {
        const val LIBRARY_ID = "library-id"
        val LIBRARY = LibraryEntity(
            id = LIBRARY_ID,
            name = "测试库",
            folderUri = "content://test/library",
            createdAt = 1L
        )
        val ORPHAN = attachment(name = "orphan.jpg")
        val REFERENCED = attachment(
            name = "used.jpg",
            references = listOf(AttachmentReference("content://test/note", "note"))
        )
        val REFERENCE_MAP = mapOf(REFERENCED.uri to REFERENCED.referencedBy)
        val QUICK_ATTACHMENTS = listOf(
            ORPHAN,
            REFERENCED.copy(referencedBy = emptyList())
        )

        fun attachment(
            name: String,
            references: List<AttachmentReference> = emptyList()
        ) = ManagedAttachment(
            displayName = name,
            relativePath = "attachments/$name",
            uri = "content://test/$name",
            mimeType = "image/jpeg",
            size = 1024,
            lastModified = 1L,
            width = 100,
            height = 100,
            referencedBy = references
        )
    }
}
