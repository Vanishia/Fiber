package com.bird.fiber.ui.screens.sidebar.heatmap

import androidx.lifecycle.ViewModelStore
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.MarkdownHeatmapEntry
import com.bird.fiber.utils.TestCoroutineRule
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeatmapViewModelTest {
    @get:Rule val coroutines = TestCoroutineRule()
    private val viewModels = ViewModelStore()
    private val testZone = ZoneId.of("Asia/Shanghai")
    private var now = Instant.parse("2026-11-22T15:59:59Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = testZone
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        override fun instant(): Instant = now
    }

    @After fun tearDown() { viewModels.clear() }

    @Test fun `累计记录天数去重跨库日期并沿用热力图字数口径`() = runTest {
        val rows = MutableStateFlow(listOf(
            entry("26-11-22_09-00-00", 100),
            entry("26-11-22_10-00-00", 250),
            entry("普通笔记", 50),
            entry("26-11-20_08-00-00", 200),
            entry("26-11-23_09-00-00", 999)
        ))
        val viewModel = viewModel(rows)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoaded)
        assertEquals(2, viewModel.uiState.value.recordedDays)
        assertEquals(400L, viewModel.uiState.value.todayChars)

        rows.value = rows.value.drop(1)
        runCurrent()
        assertEquals(300L, viewModel.uiState.value.todayChars)
        rows.value = emptyList()
        runCurrent()
        assertEquals(0, viewModel.uiState.value.recordedDays)
        assertEquals(0L, viewModel.uiState.value.todayChars)
    }

    @Test fun `跨过本地午夜后无需编辑笔记即可刷新今日统计`() = runTest {
        val viewModel = viewModel(MutableStateFlow(listOf(entry("普通笔记", 80))))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        assertEquals(80L, viewModel.uiState.value.todayChars)

        now = now.plusSeconds(1)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(0L, viewModel.uiState.value.todayChars)
        assertEquals(1, viewModel.uiState.value.recordedDays)
        assertEquals(23, viewModel.uiState.value.weeks.last().last { !it.isFuture }.date.dayOfMonth)
    }

    private fun entry(name: String, chars: Int) = MarkdownHeatmapEntry(
        name, Instant.parse("2026-11-22T02:00:00Z").toEpochMilli(), chars
    )

    private fun viewModel(rows: MutableStateFlow<List<MarkdownHeatmapEntry>>): HeatmapViewModel {
        val dao = mockk<MarkdownFileDao>()
        every { dao.observeHeatmapEntries() } returns rows
        return HeatmapViewModel(dao, coroutines.testDispatcher, clock).also { viewModels.put("heatmap", it) }
    }
}
