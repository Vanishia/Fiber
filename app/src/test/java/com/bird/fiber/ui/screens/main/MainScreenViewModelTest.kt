package com.bird.fiber.ui.screens.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * MainScreenViewModel 单元测试
 *
 * 测试主屏幕功能：
 * - 库切换
 * - UI 状态管理
 * - 激活库观察
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var libraryRepository: LibraryRepository
    private lateinit var viewModel: MainScreenViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        libraryRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== 初始状态测试 ====================

    @Test
    fun initialState_allFieldsNull() {
        // Arrange
        every { libraryRepository.getActiveLibrary() } returns flowOf(null)

        // Act
        viewModel = MainScreenViewModel(libraryRepository)

        // Assert
        val state = viewModel.uiState.value
        assertNull(state.selectedLibraryId)
        assertNull(state.currentLibraryName)
        assertNull(state.currentLibraryUri)
    }

    // ==================== 库观察测试 ====================

    @Test
    fun activeLibraryEmits_updatesUiState() = runTest {
        // Arrange
        val library = LibraryEntity(
            id = "lib1",
            name = "测试库",
            folderUri = "content://test/folder",
            createdAt = 1000,
            lastOpenedAt = 2000,
            isActive = true
        )
        every { libraryRepository.getActiveLibrary() } returns flowOf(library)

        // Act
        viewModel = MainScreenViewModel(libraryRepository)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("lib1", state.selectedLibraryId)
        assertEquals("测试库", state.currentLibraryName)
        assertEquals("content://test/folder", state.currentLibraryUri)
    }

    @Test
    fun activeLibraryChanges_updatesUiState() = runTest {
        // Arrange
        val library1 = LibraryEntity("1", "库1", "uri1", 1000, 2000, true)
        val library2 = LibraryEntity("2", "库2", "uri2", 1000, 2000, true)

        val flow = MutableStateFlow<LibraryEntity?>(library1)
        every { libraryRepository.getActiveLibrary() } returns flow

        viewModel = MainScreenViewModel(libraryRepository)
        advanceUntilIdle()

        // Pre-assert
        assertEquals("库1", viewModel.uiState.value.currentLibraryName)

        // Act
        flow.value = library2
        advanceUntilIdle()

        // Assert
        assertEquals("2", viewModel.uiState.value.selectedLibraryId)
        assertEquals("库2", viewModel.uiState.value.currentLibraryName)
    }

    // ==================== 库选择测试 ====================

    @Test
    fun onLibrarySelected_updatesSelectedLibraryId() {
        // Arrange
        every { libraryRepository.getActiveLibrary() } returns flowOf(null)
        viewModel = MainScreenViewModel(libraryRepository)

        // Act
        viewModel.onLibrarySelected("lib2")

        // Assert
        assertEquals("lib2", viewModel.uiState.value.selectedLibraryId)
    }

    @Test
    fun onLibrarySelected_multipleTimes_updatesCorrectly() {
        // Arrange
        every { libraryRepository.getActiveLibrary() } returns flowOf(null)
        viewModel = MainScreenViewModel(libraryRepository)

        // Act
        viewModel.onLibrarySelected("lib1")
        viewModel.onLibrarySelected("lib2")
        viewModel.onLibrarySelected("lib3")

        // Assert
        assertEquals("lib3", viewModel.uiState.value.selectedLibraryId)
    }

    // ==================== 边界情况测试 ====================

    @Test
    fun activeLibraryWithNullFields_handlesCorrectly() = runTest {
        // Arrange
        val library = LibraryEntity(
            id = "lib1",
            name = "",
            folderUri = "",
            createdAt = 0,
            lastOpenedAt = 0,
            isActive = false
        )
        every { libraryRepository.getActiveLibrary() } returns flowOf(library)

        // Act
        viewModel = MainScreenViewModel(libraryRepository)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("lib1", state.selectedLibraryId)
        assertEquals("", state.currentLibraryName)
        assertEquals("", state.currentLibraryUri)
    }

    @Test
    fun activeLibraryFlowCompletes_stateRemains() = runTest {
        // Arrange
        val library = LibraryEntity("1", "库1", "uri1", 1000, 2000, true)
        every { libraryRepository.getActiveLibrary() } returns flowOf(library)

        // Act
        viewModel = MainScreenViewModel(libraryRepository)
        advanceUntilIdle()

        // Assert
        assertEquals("库1", viewModel.uiState.value.currentLibraryName)
    }
}
