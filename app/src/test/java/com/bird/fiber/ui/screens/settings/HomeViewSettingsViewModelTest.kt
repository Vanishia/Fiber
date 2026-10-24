package com.bird.fiber.ui.screens.settings

import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.settings.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val libraries = MutableStateFlow(listOf(
        LibraryEntity("a", "同名库", "uri-a", 0),
        LibraryEntity("b", "同名库", "uri-b", 0)
    ))
    private val settings = MutableStateFlow(SettingsUiState())
    private val repository = mockk<LibraryRepository>()
    private val store = mockk<SettingsDataStore>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.getAllLibraries() } returns libraries
        every { store.settingsFlow } returns settings
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `加载真实库列表并按库ID解析视图且响应库导入删除`() = runTest {
        val viewModel = HomeViewSettingsViewModel(repository, store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf(ListViewMode.LIST, ListViewMode.LIST), viewModel.uiState.value.libraries.map { it.viewMode })
        settings.value = SettingsUiState(waterfallLibraryIds = setOf("b"))
        assertEquals(listOf(ListViewMode.LIST, ListViewMode.GRID), viewModel.uiState.value.libraries.map { it.viewMode })
        libraries.value = listOf(libraries.value[1].copy(name = "改名库"), LibraryEntity("c", "新库", "uri-c", 0))
        assertEquals(listOf("改名库", "新库"), viewModel.uiState.value.libraries.map { it.libraryName })
        assertEquals(listOf(ListViewMode.GRID, ListViewMode.LIST), viewModel.uiState.value.libraries.map { it.viewMode })
        libraries.value = emptyList()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(emptyList<LibraryHomeViewSetting>(), viewModel.uiState.value.libraries)
    }

    @Test
    fun `选择视图仅保存指定库`() = runTest {
        coEvery { store.saveLibraryListViewMode(any(), any()) } returns Unit
        val viewModel = HomeViewSettingsViewModel(repository, store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.setLibraryViewMode("b", ListViewMode.GRID)
        coVerify(exactly = 1) { store.saveLibraryListViewMode("b", ListViewMode.GRID) }
        coVerify(exactly = 0) { store.saveLibraryListViewMode("a", any()) }
        viewModel.setLibraryViewMode("removed", ListViewMode.GRID)
        coVerify(exactly = 0) { store.saveLibraryListViewMode("removed", any()) }
    }

    @Test
    fun `保存失败提示重试且不会把未保存的模式显示为成功`() = runTest {
        coEvery { store.saveLibraryListViewMode(any(), any()) } throws IOException("disk full")
        val viewModel = HomeViewSettingsViewModel(repository, store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.setLibraryViewMode("a", ListViewMode.GRID)
        assertEquals("保存失败，请重试", viewModel.errorMessage.value)
        assertEquals(ListViewMode.LIST, viewModel.uiState.value.libraries.first().viewMode)
        viewModel.clearError()
        assertNull(viewModel.errorMessage.value)
    }
}
