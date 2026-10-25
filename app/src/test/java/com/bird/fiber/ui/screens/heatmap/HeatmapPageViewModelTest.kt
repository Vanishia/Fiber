package com.bird.fiber.ui.screens.heatmap

import androidx.lifecycle.ViewModelStore
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownHeatmapEntry
import com.bird.fiber.utils.TestCoroutineRule
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeatmapPageViewModelTest {
    @get:Rule val coroutines = TestCoroutineRule()
    private val viewModels = ViewModelStore()
    private val historicalYear = LocalDate.now().year - 1
    private val january = YearMonth.of(historicalYear, 1)
    private val initialRows = listOf(
        entry("一月笔记", january.atDay(2)),
        entry("六月笔记", LocalDate.of(historicalYear, 6, 3)),
        entry("近期笔记", LocalDate.now().minusDays(1))
    )

    @After fun tearDown() { viewModels.clear() }

    @Test
    fun `年月与最近一年切换直接发布完整网格且无需推进时间`() = runTest {
        val viewModel = viewModel(MutableStateFlow(initialRows))
        val states = mutableListOf<HeatmapPageUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }
        runCurrent()
        assertTrue(viewModel.uiState.value.weeks.isNotEmpty())

        viewModel.selectFilter(HeatmapFilter.Month(january))
        runCurrent()
        assertEquals(HeatmapFilter.Month(january), viewModel.uiState.value.filter)
        assertTrue(viewModel.uiState.value.monthRows.isNotEmpty())
        assertEquals(1, viewModel.uiState.value.noteStats.periodCount)

        viewModel.selectFilter(HeatmapFilter.Year(historicalYear))
        runCurrent()
        assertEquals(HeatmapFilter.Year(historicalYear), viewModel.uiState.value.filter)
        assertTrue(viewModel.uiState.value.weeks.isNotEmpty())
        assertEquals(2, viewModel.uiState.value.noteStats.periodCount)

        viewModel.selectFilter(HeatmapFilter.All)
        runCurrent()
        assertEquals(HeatmapFilter.All, viewModel.uiState.value.filter)
        assertEquals(0L, testScheduler.currentTime)
        assertCompleteStates(states)
    }

    @Test
    fun `索引更新直接刷新当前月份的网格和统计且不清空旧结果`() = runTest {
        val rows = MutableStateFlow(initialRows)
        val viewModel = viewModel(rows)
        val states = mutableListOf<HeatmapPageUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }
        viewModel.selectFilter(HeatmapFilter.Month(january))
        runCurrent()
        rows.value += entry("新增一月笔记", january.atDay(5))
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(HeatmapFilter.Month(january), state.filter)
        assertEquals(2, state.noteStats.periodCount)
        assertEquals(4, state.noteStats.totalCount)
        assertEquals(2, state.monthRows.flatten().filterNotNull().sumOf { it.count })
        assertEquals(2, state.libraryNoteCounts.sumOf { it.count })
        assertEquals(0L, testScheduler.currentTime)
        assertCompleteStates(states)
    }

    @Test
    fun `快速连续切换只展示最终筛选条件的完整结果`() = runTest {
        val viewModel = viewModel(MutableStateFlow(initialRows))
        val states = mutableListOf<HeatmapPageUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }
        runCurrent()
        viewModel.selectFilter(HeatmapFilter.Year(historicalYear))
        viewModel.selectFilter(HeatmapFilter.All)
        viewModel.selectFilter(HeatmapFilter.Month(january))
        runCurrent()

        assertEquals(HeatmapFilter.Month(january), viewModel.uiState.value.filter)
        assertEquals(1, viewModel.uiState.value.noteStats.periodCount)
        assertEquals(0L, testScheduler.currentTime)
        assertCompleteStates(states)
    }

    @Test
    fun `首次查询完成前保留加载状态空索引到达后正常显示空日历`() = runTest {
        val rows = MutableSharedFlow<List<MarkdownHeatmapEntry>>()
        val viewModel = viewModel(rows)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoaded)

        rows.emit(emptyList())
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoaded)
        assertTrue(viewModel.uiState.value.weeks.isNotEmpty())
        assertEquals(0, viewModel.uiState.value.noteStats.totalCount)

        viewModel.selectFilter(HeatmapFilter.Month(january))
        runCurrent()
        assertTrue(viewModel.uiState.value.monthRows.isNotEmpty())
        assertEquals(0, viewModel.uiState.value.noteStats.periodCount)
        assertEquals(0L, testScheduler.currentTime)
    }

    private fun viewModel(rows: Flow<List<MarkdownHeatmapEntry>>): HeatmapPageViewModel {
        val dao = mockk<MarkdownFileDao>()
        every { dao.observeHeatmapEntries() } returns rows
        return HeatmapPageViewModel(dao, coroutines.testDispatcher).also { viewModels.put("heatmap", it) }
    }

    private fun assertCompleteStates(states: List<HeatmapPageUiState>) {
        val loaded = states.filter { it.isLoaded }
        assertTrue(loaded.isNotEmpty())
        loaded.forEach { state ->
            if (state.filter is HeatmapFilter.Month) {
                assertTrue("月份结果不能插入空白过渡帧", state.monthRows.isNotEmpty())
            } else {
                assertTrue("年份与最近一年结果不能插入空白过渡帧", state.weeks.isNotEmpty())
            }
        }
    }

    private fun entry(name: String, day: LocalDate) = MarkdownHeatmapEntry(
        name, day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        100, "a", "库 A"
    )
}
