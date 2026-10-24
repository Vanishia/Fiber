package com.bird.fiber.ui.screens.filelist

import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.local.PreviewCache
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.settings.SettingsDataStore
import com.bird.fiber.ui.screens.settings.ListViewMode
import com.bird.fiber.ui.screens.settings.SettingsUiState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileListViewModeTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `主页切库及修改设置时读取当前库的独立视图`() = runTest {
        val library = LibraryEntity("a", "库 A", "uri-a", 0)
        val activeLibrary = MutableStateFlow<LibraryEntity?>(library)
        val settings = MutableStateFlow(SettingsUiState(waterfallLibraryIds = setOf("a")))
        val repository = mockk<LibraryRepository>()
        val store = mockk<SettingsDataStore>()
        val events = mockk<EventBus>()
        every { repository.getActiveLibrary() } returns activeLibrary
        every { store.settingsFlow } returns settings
        every { store.quickSearchKeywordsFlow } returns flowOf(emptyList())
        every { events.events } returns MutableSharedFlow<AppEvent>()
        val viewModel = FileListViewModel(mockk(), events, mockk(), repository, PreviewCache(), mockk(), store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.listViewMode.collect() }
        assertEquals(ListViewMode.GRID, viewModel.listViewMode.value)
        activeLibrary.value = library.copy(id = "b", name = "库 B")
        assertEquals(ListViewMode.LIST, viewModel.listViewMode.value)
        settings.value = SettingsUiState(waterfallLibraryIds = setOf("a", "b"))
        assertEquals(ListViewMode.GRID, viewModel.listViewMode.value)
        settings.value = SettingsUiState(waterfallLibraryIds = setOf("a"))
        assertEquals(ListViewMode.LIST, viewModel.listViewMode.value)
        activeLibrary.value = library.copy(name = "重命名库")
        assertEquals(ListViewMode.GRID, viewModel.listViewMode.value)
        activeLibrary.value = null
        assertEquals(ListViewMode.LIST, viewModel.listViewMode.value)
    }
}
