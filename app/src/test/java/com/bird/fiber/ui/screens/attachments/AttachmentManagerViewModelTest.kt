package com.bird.fiber.ui.screens.attachments

import androidx.lifecycle.SavedStateHandle
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.model.AttachmentDeletionSummary
import com.bird.fiber.data.model.AttachmentReference
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.ManagedAttachment
import com.bird.fiber.data.repository.AttachmentRepository
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        attachmentRepository = mockk()
        libraryRepository = mockk()
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

    private fun createViewModel() = AttachmentManagerViewModel(
        attachmentRepository = attachmentRepository,
        libraryRepository = libraryRepository,
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
