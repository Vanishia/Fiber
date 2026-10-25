package com.bird.fiber.ui.screens.importing

import com.bird.fiber.data.importing.ImportShareManager
import com.bird.fiber.data.importing.PendingImport
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.FileError
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.data.repository.AttachmentRepository
import com.bird.fiber.data.repository.FileRepository
import com.bird.fiber.domain.usecase.GenerateFileNameUseCase
import com.bird.fiber.utils.TestCoroutineRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    @get:Rule val coroutines = TestCoroutineRule()

    private val imports = ImportShareManager()
    private val files = mockk<FileRepository>()
    private val libraries = mockk<LibraryRepository>()
    private val library = LibraryEntity("library", "笔记库", "content://library", 0L)
    private val pending = PendingImport("分享.md", "# 分享的笔记")
    private val saved = MarkdownFileMeta("content://library/shared.md", "分享", "分享.md", 0L, 0L)
    private lateinit var viewModel: ImportViewModel

    @Before
    fun setUp() {
        every { libraries.getAllLibraries() } returns flowOf(listOf(library))
        viewModel = ImportViewModel(
            imports, files, mockk<AttachmentRepository>(), mockk<GenerateFileNameUseCase>(), libraries
        )
        imports.offer(pending)
    }

    @Test
    fun repeatedSelectionWhileSavingCreatesOnlyOneFile() = runTest {
        val result = CompletableDeferred<FileResult<MarkdownFileMeta>>()
        coEvery { files.createMarkdownFile(any(), any(), any()) } coAnswers { result.await() }

        // 两次点击发生在保存协程获得执行机会之前。
        viewModel.saveToLibrary(library)
        viewModel.saveToLibrary(library)
        runCurrent()

        try {
            coVerify(exactly = 1) { files.createMarkdownFile(any(), pending.fileName, pending.content) }
        } finally {
            result.complete(FileResult.Success(saved))
            runCurrent()
        }
    }

    @Test
    fun completingPreviousSaveDoesNotConsumeNewShare() = runTest {
        val result = CompletableDeferred<FileResult<MarkdownFileMeta>>()
        coEvery { files.createMarkdownFile(any(), any(), any()) } coAnswers { result.await() }
        viewModel.saveToLibrary(library)
        runCurrent()

        val next = PendingImport("下一份.md", "下一份内容")
        imports.offer(next)
        result.complete(FileResult.Success(saved))
        runCurrent()

        assertEquals(next, viewModel.pendingImport.value)
    }

    @Test
    fun completingPreviousSaveDoesNotConsumeRepeatedShareOfSameFile() = runTest {
        val result = CompletableDeferred<FileResult<MarkdownFileMeta>>()
        coEvery { files.createMarkdownFile(any(), any(), any()) } coAnswers { result.await() }
        viewModel.saveToLibrary(library)
        runCurrent()

        val next = PendingImport(pending.fileName, pending.content)
        imports.offer(next)
        result.complete(FileResult.Success(saved))
        runCurrent()

        assertEquals(next, viewModel.pendingImport.value)
    }

    @Test
    fun successWithoutCollectorKeepsNavigationUntilAcknowledged() = runTest {
        coEvery { files.createMarkdownFile(any(), any(), any()) } returns FileResult.Success(saved)
        viewModel.saveToLibrary(library)
        assertTrue(viewModel.isSaving.value)
        runCurrent()

        assertFalse(viewModel.isSaving.value)
        assertNull(viewModel.pendingImport.value)
        assertEquals(saved.uri, viewModel.openEditor.value)
        viewModel.onEditorOpened("content://another-file")
        assertEquals(saved.uri, viewModel.openEditor.value)
        viewModel.onEditorOpened(saved.uri)
        assertNull(viewModel.openEditor.value)
    }

    @Test
    fun dismissWhileSavingDoesNotClearInFlightImport() = runTest {
        val result = CompletableDeferred<FileResult<MarkdownFileMeta>>()
        coEvery { files.createMarkdownFile(any(), any(), any()) } coAnswers { result.await() }
        viewModel.saveToLibrary(library)
        runCurrent()
        viewModel.dismiss()

        assertEquals(pending, viewModel.pendingImport.value)
        assertTrue(viewModel.isSaving.value)
        result.complete(FileResult.Success(saved))
        runCurrent()
        assertNull(viewModel.pendingImport.value)
        assertFalse(viewModel.isSaving.value)
    }

    @Test
    fun saveFailureReleasesSavingStateWithoutNavigating() = runTest {
        coEvery { files.createMarkdownFile(any(), any(), any()) } returns
            FileResult.Error(FileError.Unknown("测试失败"))
        viewModel.saveToLibrary(library)
        runCurrent()

        assertFalse(viewModel.isSaving.value)
        assertNull(viewModel.openEditor.value)
    }

    @Test
    fun unexpectedSaveExceptionReleasesSavingStateWithoutNavigating() = runTest {
        coEvery { files.createMarkdownFile(any(), any(), any()) } throws
            IllegalStateException("测试异常")
        viewModel.saveToLibrary(library)
        runCurrent()

        assertFalse(viewModel.isSaving.value)
        assertNull(viewModel.openEditor.value)
    }

    @Test
    fun dismissClearsPendingImportWithoutCreatingFile() = runTest {
        viewModel.dismiss()
        viewModel.saveToLibrary(library)
        runCurrent()

        assertNull(viewModel.pendingImport.value)
        coVerify(exactly = 0) { files.createMarkdownFile(any(), any(), any()) }
    }
}
