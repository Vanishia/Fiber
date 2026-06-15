package com.bird.fiber.ui.screens.editor

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import android.text.Spanned
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.model.FileError
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.repository.FileRepository
import com.bird.fiber.domain.usecase.RenderMarkdownUseCase
import com.bird.fiber.utils.TestCoroutineRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * EditorViewModel 单元测试
 *
 * 测试编辑器功能：
 * - 文件加载
 * - 内容编辑
 * - 文件保存
 * - 未保存修改检测
 * - 预览模式切换
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private lateinit var fileRepository: FileRepository
    private lateinit var eventBus: EventBus
    private lateinit var renderMarkdownUseCase: RenderMarkdownUseCase
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        fileRepository = mockk(relaxed = true)
        eventBus = mockk(relaxed = true)
        renderMarkdownUseCase = mockk(relaxed = true)
        every { renderMarkdownUseCase.render(any()) } returns mockk<Spanned>(relaxed = true)
        viewModel = EditorViewModel(
            fileRepository,
            eventBus,
            renderMarkdownUseCase,
            coroutineRule.testDispatcher
        )
    }

    // ==================== 文件加载测试 ====================

    @Test
    fun loadFile_success_updatesUiState() = runTest {
        // Arrange
        val fileUri = "content://test/file.md"
        val fileContent = "测试内容"

        coEvery { fileRepository.readFileContent(fileUri) } returns FileResult.Success(fileContent)

        // Act
        viewModel.loadFile(fileUri)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(fileContent, state.content)
        assertNull(state.error)
    }

    @Test
    fun loadFile_error_updatesErrorState() = runTest {
        // Arrange
        val fileUri = "content://test/file.md"
        val error = FileError.NotFound(fileUri)

        coEvery { fileRepository.readFileContent(fileUri) } returns FileResult.Error(error)

        // Act
        viewModel.loadFile(fileUri)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }

    @Test
    fun loadFile_loadingState_setToTrueInitially() = runTest {
        // Arrange
        val fileUri = "content://test/file.md"

        coEvery { fileRepository.readFileContent(fileUri) } coAnswers {
            kotlinx.coroutines.delay(100)
            FileResult.Success("内容")
        }

        // Act
        viewModel.loadFile(fileUri)

        // Assert - 在协程完成前，isLoading 应该为 true
        val initialState = viewModel.uiState.value
        assertTrue(initialState.isLoading)

        advanceUntilIdle()
    }

    // ==================== 内容编辑测试 ====================

    @Test
    fun onContentChange_updatesContent() {
        // Arrange
        val newContent = "新内容"

        // Act
        viewModel.onContentChange(newContent)

        // Assert
        assertEquals(newContent, viewModel.uiState.value.content)
    }

    @Test
    fun hasUnsavedChanges_contentModified_returnsTrue() = runTest {
        // Arrange
        val fileUri = "content://test/file.md"
        val originalContent = "原始内容"

        coEvery { fileRepository.readFileContent(fileUri) } returns FileResult.Success(originalContent)
        viewModel.loadFile(fileUri)
        advanceUntilIdle()

        // Act
        viewModel.onContentChange("修改后的内容")

        // Assert
        assertTrue(viewModel.hasUnsavedChanges())
    }

    @Test
    fun hasUnsavedChanges_contentNotModified_returnsFalse() = runTest {
        // Arrange
        val fileUri = "content://test/file.md"
        val originalContent = "原始内容"

        coEvery { fileRepository.readFileContent(fileUri) } returns FileResult.Success(originalContent)
        viewModel.loadFile(fileUri)
        advanceUntilIdle()

        // Act - 不做修改

        // Assert
        assertFalse(viewModel.hasUnsavedChanges())
    }

    @Test
    fun hasUnsavedChanges_contentRevertedToOriginal_returnsFalse() = runTest {
        // Arrange
        val fileUri = "content://test/file.md"
        val originalContent = "原始内容"

        coEvery { fileRepository.readFileContent(fileUri) } returns FileResult.Success(originalContent)
        viewModel.loadFile(fileUri)
        advanceUntilIdle()

        // Act
        viewModel.onContentChange("修改后的内容")
        viewModel.onContentChange(originalContent)

        // Assert
        assertFalse(viewModel.hasUnsavedChanges())
    }

    // ==================== 保存文件测试 ====================

    @Test
    fun saveFile_success_emitsEvent() = runTest {
        // Arrange
        val fileUri = "content://test/file.md"
        val content = "保存内容"

        coEvery { fileRepository.readFileContent(fileUri) } returns FileResult.Success("")
        coEvery { fileRepository.saveFileContent(fileUri, any()) } returns FileResult.Success(Unit)
        coEvery { eventBus.emit(any<AppEvent.FileUpdated>()) } just Runs

        viewModel.loadFile(fileUri)
        advanceUntilIdle()

        viewModel.onContentChange(content)

        // Act
        viewModel.saveFile()
        advanceUntilIdle()

        // Assert
        coVerify { fileRepository.saveFileContent(fileUri, content) }
        coVerify { eventBus.emit(any<AppEvent.FileUpdated>()) }
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun saveFile_noFileLoaded_doesNothing() = runTest {
        // Act - 没有加载文件就直接保存
        viewModel.saveFile()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { fileRepository.saveFileContent(any(), any()) }
    }

    @Test
    fun saveFile_error_updatesErrorState() = runTest {
        // Arrange
        val fileUri = "content://test/file.md"
        val error = FileError.PermissionDenied(fileUri)

        coEvery { fileRepository.readFileContent(fileUri) } returns FileResult.Success("")
        coEvery { fileRepository.saveFileContent(any(), any()) } returns FileResult.Error(error)

        viewModel.loadFile(fileUri)
        advanceUntilIdle()

        viewModel.onContentChange("内容")

        // Act
        viewModel.saveFile()
        advanceUntilIdle()

        // Assert
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun saveFile_savingState_isTrueDuringSave() = runTest {
        // Arrange
        val fileUri = "content://test/file.md"

        coEvery { fileRepository.readFileContent(fileUri) } returns FileResult.Success("")
        coEvery { fileRepository.saveFileContent(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(100)
            FileResult.Success(Unit)
        }

        viewModel.loadFile(fileUri)
        advanceUntilIdle()
        viewModel.onContentChange("内容")

        // Act
        viewModel.saveFile()
        runCurrent()

        // Assert - 保存开始时 isSaving 为 true
        assertTrue(viewModel.uiState.value.isSaving)

        advanceUntilIdle()
    }

    // ==================== 预览模式测试 ====================

    @Test
    fun togglePreviewMode_switchesMode() {
        // Arrange
        val initialMode = viewModel.uiState.value.isPreviewMode

        // Act
        viewModel.togglePreviewMode()

        // Assert
        assertEquals(!initialMode, viewModel.uiState.value.isPreviewMode)
    }

    @Test
    fun togglePreviewMode_twice_returnsToOriginal() {
        // Arrange
        val initialMode = viewModel.uiState.value.isPreviewMode

        // Act
        viewModel.togglePreviewMode()
        viewModel.togglePreviewMode()

        // Assert
        assertEquals(initialMode, viewModel.uiState.value.isPreviewMode)
    }

    @Test
    fun setInitialPreviewMode_setsMode() {
        // Arrange
        val targetMode = true

        // Act
        viewModel.setInitialPreviewMode(targetMode)

        // Assert
        assertEquals(targetMode, viewModel.uiState.value.isPreviewMode)
    }

    // ==================== 边界情况测试 ====================

    @Test
    fun loadFile_emptyContent_handlesCorrectly() = runTest {
        // Arrange
        val fileUri = "content://test/file.md"

        coEvery { fileRepository.readFileContent(fileUri) } returns FileResult.Success("")

        // Act
        viewModel.loadFile(fileUri)
        advanceUntilIdle()

        // Assert
        assertEquals("", viewModel.uiState.value.content)
        assertFalse(viewModel.hasUnsavedChanges())
    }

    @Test
    fun loadFile_multilineContent_preservesNewlines() = runTest {
        // Arrange
        val fileUri = "content://test/file.md"
        val content = "第一行\n第二行\n第三行"

        coEvery { fileRepository.readFileContent(fileUri) } returns FileResult.Success(content)

        // Act
        viewModel.loadFile(fileUri)
        advanceUntilIdle()

        // Assert
        assertEquals(content, viewModel.uiState.value.content)
    }
}
