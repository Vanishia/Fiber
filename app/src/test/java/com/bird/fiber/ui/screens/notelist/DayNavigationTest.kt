package com.bird.fiber.ui.screens.notelist

import androidx.lifecycle.SavedStateHandle
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.ui.screens.filelist.FileListViewModel.SearchDateRange
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DayNavigationTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `翻页跨年和闰月并保存当前日期供返回后恢复`() = runTest {
        val state = SavedStateHandle(mapOf("date" to "2024-03-01"))
        val viewModel = viewModel(state)
        viewModel.showDate(viewModel.date.value!!.minusDays(1))
        assertEquals(LocalDate.of(2024, 2, 29), viewModel.date.value)
        assertEquals("2024-02-29的笔记", viewModel.title.value)
        viewModel.showDate(LocalDate.of(2024, 12, 31).plusDays(1))
        assertEquals("2025-01-01", state.get<String>("date"))
        assertEquals(LocalDate.of(2025, 1, 1), viewModel(state).date.value)
    }

    @Test
    fun `全部笔记没有单日日期`() = runTest {
        val viewModel = viewModel(SavedStateHandle())
        assertNull(viewModel.date.value)
        assertEquals("全部笔记", viewModel.title.value)
    }

    @Test
    fun `单日筛选按本地午夜计算且支持夏令时长短日`() {
        val zone = ZoneId.of("America/New_York")
        for ((day, hours) in listOf(LocalDate.of(2026, 3, 8) to 23L, LocalDate.of(2026, 11, 1) to 25L)) {
            val range = SearchDateRange.forDay(day, zone)
            assertEquals(day, range.day)
            assertEquals(day.atStartOfDay(zone).toInstant().toEpochMilli(), range.fromMillis)
            assertEquals(hours * 60 * 60 * 1_000, range.toMillis!! - range.fromMillis!!)
        }
    }

    private fun viewModel(state: SavedStateHandle): NoteListViewModel {
        val libraries = mockk<LibraryRepository>()
        every { libraries.getActiveLibrary() } returns flowOf(null)
        return NoteListViewModel(mockk(), libraries, state)
    }
}
