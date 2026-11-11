package com.bird.fiber.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.bird.fiber.ui.navigation.FiberRoute
import com.bird.fiber.ui.screens.notelist.NoteListScreen
import com.bird.fiber.ui.screens.notelist.NoteListViewModel
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.local.PreviewCache
import com.bird.fiber.data.local.library.FiberDatabase
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.MarkdownFileEntity
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.LibraryTarget
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.data.repository.FileRepository
import com.bird.fiber.data.settings.SettingsDataStore
import com.bird.fiber.ui.screens.filelist.FileListCommandRunner
import com.bird.fiber.ui.screens.filelist.FileListViewModel
import com.bird.fiber.ui.screens.filelist.FileListViewModel.SearchDatePreset
import com.bird.fiber.ui.screens.filelist.FileListViewModel.SearchDateRange
import com.bird.fiber.ui.screens.filelist.FileListViewModel.SearchScope
import com.bird.fiber.ui.screens.filelist.FileListViewModel.SearchSort
import com.bird.fiber.ui.theme.FiberTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 真实 NavHost、搜索页、ViewModel 和 Room 分页；编辑器用返回按钮代替，隔离文件读写。 */
class SearchNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: FiberDatabase
    private lateinit var settingsFile: File
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settings: SettingsDataStore
    private lateinit var navController: NavHostController
    private lateinit var viewModel: FileListViewModel
    private val searchDisposals = AtomicInteger()
    private val dateRange = SearchDateRange(SearchDatePreset.CUSTOM, 1_000L, 5_000L)

    @Before
    fun setUp(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FiberDatabase::class.java).build()
        database.libraryDao().insertLibrary(LibraryEntity("a", "工作库", "content://test/a", 0, isActive = true))
        database.libraryDao().insertLibrary(LibraryEntity("b", "生活库", "content://test/b", 0))
        database.markdownFileDao().insertAll((0 until 160).map { index ->
            MarkdownFileEntity(
                uri = "content://test/note/$index",
                name = "项目复盘 $index",
                path = "复盘/$index.md",
                lastModified = 1_001L + index,
                size = 100,
                libraryId = if (index < 80) "a" else "b",
                contentPreview = "搜索导航回归测试",
                contentText = "项目复盘：搜索返回时应保留上下文"
            )
        })
        settingsFile = File(context.cacheDir, "search-navigation-${System.nanoTime()}.preferences_pb")
        settings = SettingsDataStore(PreferenceDataStoreFactory.create(scope = settingsScope) { settingsFile })
    }

    @After
    fun tearDown() {
        if (::navController.isInitialized) {
            compose.runOnIdle { navController.popBackStack(navController.graph.startDestinationId, inclusive = true) }
            compose.waitForIdle()
        }
        settingsScope.cancel()
        database.close()
        settingsFile.delete()
    }

    @Test
    fun returningFromNoteKeepsCurrentLibrarySearchAndScroll() {
        checkReturnToSearch(SearchScope.CURRENT_LIBRARY)
    }

    @Test
    fun returningFromOtherLibraryNoteKeepsGlobalSearchAndScroll() {
        checkReturnToSearch(SearchScope.ALL_LIBRARIES)
    }

    @Test
    fun dayArrowsReplaceDateAndSearchCarriesCurrentDay() {
        val day = LocalDate.of(2026, 12, 31)
        val start = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nextStart = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking {
            database.markdownFileDao().insertAll(listOf(
                MarkdownFileEntity("content://test/quick", "26-12-31_09-00-00", "quick.md", nextStart + 1_000, 100, "a", contentText = "跨年计划"),
                MarkdownFileEntity("content://test/day", "当天普通笔记", "day.md", start + 1_000, 100, "b", contentText = "跨年计划"),
                MarkdownFileEntity("content://test/next", "次日笔记", "next.md", nextStart + 1_000, 100, "b", contentText = "跨年计划")
            ))
        }
        compose.setContent {
            FiberTheme(dynamicColor = false) {
                navController = rememberNavController()
                NavHost(navController, startDestination = "origin") {
                    composable("origin") {
                        TextButton(onClick = { navController.navigate(FiberRoute.dayNotes(day)) }) { Text("来源页") }
                    }
                    composable(FiberRoute.DAY_NOTES, arguments = listOf(navArgument("date") { type = NavType.StringType })) { entry ->
                        val factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T = NoteListViewModel(
                                database.markdownFileDao(), LibraryRepository(database.libraryDao()),
                                SavedStateHandle(mapOf("date" to entry.arguments?.getString("date")))
                            ) as T
                        }
                        val daily = ViewModelProvider(entry, factory)[NoteListViewModel::class.java]
                        val selectedDay by daily.date.collectAsStateWithLifecycle()
                        val title by daily.title.collectAsStateWithLifecycle()
                        val empty by daily.emptyText.collectAsStateWithLifecycle()
                        NoteListScreen(
                            title = title, emptyText = empty, pager = daily.pager,
                            onMenuClick = {}, onFileClick = { _, _ -> },
                            onSearchClick = { navController.navigate(FiberRoute.search(selectedDay)) },
                            date = selectedDay, onDayChange = daily::showDate,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable(FiberRoute.SEARCH_ROUTE, arguments = listOf(navArgument("date") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    })) { entry ->
                        viewModel = ViewModelProvider(entry, searchFactory(entry.arguments?.getString("date")))[FileListViewModel::class.java]
                        SearchScreen(
                            onBackClick = { navController.popBackStack() },
                            onFileClick = { navController.navigate("note") }, viewModel = viewModel
                        )
                    }
                    composable("note") {
                        TextButton(onClick = { navController.popBackStack() }) { Text("返回搜索结果") }
                    }
                }
            }
        }
        compose.onNodeWithText("来源页").performClick()
        compose.onNodeWithText(day.toString()).assertIsDisplayed()
        val dateBounds = compose.onNodeWithText(day.toString()).fetchSemanticsNode().boundsInRoot
        val screenBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(screenBounds.center.x, dateBounds.center.x, 1f)
        val dayEntry = navController.currentBackStackEntry
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("当天普通笔记")).fetchSemanticsNodes().isNotEmpty() }
        val screenshot = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "day-notes-top-bar.png")
        screenshot.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }

        compose.onNodeWithContentDescription("后一天").performClick()
        compose.onNodeWithText("2027-01-01").assertIsDisplayed()
        compose.onNodeWithContentDescription("后一天").performClick()
        compose.onNodeWithText("2027-01-02").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("这一天没有笔记")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("前一天").performClick()
        compose.onNodeWithText("2027-01-01").assertIsDisplayed()
        compose.runOnIdle { assertSame(dayEntry, navController.currentBackStackEntry) }

        compose.onNodeWithContentDescription("搜索当天笔记").performClick()
        compose.onNodeWithText("2027-01-01的笔记").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("次日笔记")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("当天普通笔记").assertDoesNotExist()
        compose.runOnIdle { assertEquals(LocalDate.of(2027, 1, 1), viewModel.searchDateRange.value.day) }
        compose.onNode(hasSetTextAction()).performTextInput("跨年")
        compose.onNodeWithText("次日笔记").performClick()
        compose.onNodeWithText("返回搜索结果").performClick()
        compose.onNodeWithText("2027-01-01的笔记").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("跨年")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("2027-01-01").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("来源页").assertIsDisplayed()
    }

    private fun searchFactory(day: String? = null) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FileListViewModel(
            FileListCommandRunner(UnusedFiles), EventBus(), database.markdownFileDao(),
            LibraryRepository(database.libraryDao()), PreviewCache(), UnusedFiles, settings,
            SavedStateHandle(mapOf("date" to day))
        ) as T
    }

    private fun checkReturnToSearch(scope: SearchScope) {
        val factory = searchFactory()
        compose.setContent {
            FiberTheme(dynamicColor = false) {
                navController = rememberNavController()
                NavHost(navController, startDestination = "search") {
                    composable("search") { entry ->
                        viewModel = ViewModelProvider(entry, factory)[FileListViewModel::class.java]
                        DisposableEffect(Unit) {
                            onDispose { searchDisposals.incrementAndGet() }
                        }
                        SearchScreen(
                            onBackClick = { navController.popBackStack() },
                            onFileClick = { navController.navigate("note") },
                            viewModel = viewModel
                        )
                    }
                    composable("note") {
                        Column {
                            Text("笔记详情")
                            TextButton(onClick = { navController.popBackStack() }) { Text("返回搜索结果") }
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { ::viewModel.isInitialized && viewModel.uiState.value.hasResolvedInitialLibrary }
        compose.runOnIdle {
            viewModel.updateSearchScope(scope)
            viewModel.updateSearchSort(SearchSort.RECENT_MODIFIED)
            viewModel.updateSearchDateRange(dateRange)
        }
        compose.onNode(hasSetTextAction()).performTextInput("项目复盘")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(androidx.compose.ui.test.hasText("项目复盘 ${if (scope == SearchScope.CURRENT_LIBRARY) 79 else 159}"))
                    .fetchSemanticsNodes().isNotEmpty()
        }

        // 超过初始 40 条，确保返回时保留的不只是第一页；索引 0 是搜索摘要。
        for (index in listOf(25, 45, 65)) {
            compose.onNode(hasScrollAction()).performScrollToIndex(index)
            compose.waitForIdle()
        }
        // 模拟停在行内的真实滑动位置，返回时像素偏移也应一致。
        compose.onNode(hasScrollAction()).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 64f, durationMillis = 1_000)
        }
        val noteName = "项目复盘 ${if (scope == SearchScope.CURRENT_LIBRARY) 15 else 95}"
        val row = compose.onNodeWithText(noteName).assertIsDisplayed()
        val rowTop = row.fetchSemanticsNode().boundsInRoot.top
        val originalViewModel = viewModel
        row.performClick()
        compose.onNodeWithText("笔记详情").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                "content://test/${if (scope == SearchScope.CURRENT_LIBRARY) "a" else "b"}",
                viewModel.uiState.value.currentFolderUri
            )
        }
        compose.waitUntil(10_000) { searchDisposals.get() > 0 }
        compose.onNodeWithText("返回搜索结果").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("项目复盘")
        compose.onNodeWithText(noteName).assertIsDisplayed()
        assertEquals(rowTop, compose.onNodeWithText(noteName).fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle {
            assertSame(originalViewModel, viewModel)
            assertEquals(scope, viewModel.searchScope.value)
            assertEquals(SearchSort.RECENT_MODIFIED, viewModel.searchSort.value)
            assertEquals(dateRange, viewModel.searchDateRange.value)
        }

        // 连续打开笔记并再次返回，结果页仍应停在原处。
        compose.onNodeWithText(noteName).performClick()
        compose.onNodeWithText("返回搜索结果").performClick()
        compose.onNodeWithText(noteName).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("项目复盘")

        // 主动清除仍生效；重新进入一轮搜索时才使用默认条件。
        compose.onNodeWithContentDescription("清除").performClick()
        compose.onNodeWithContentDescription("清除日期筛选").performClick()
        compose.onNodeWithText("搜索范围").assertIsDisplayed()
        compose.runOnIdle { assertEquals("", viewModel.searchQueryInput.value) }
        compose.runOnIdle {
            navController.popBackStack("search", inclusive = true)
            navController.navigate("search")
        }
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
        )
        compose.runOnIdle {
            assertEquals(SearchScope.ALL_LIBRARIES, viewModel.searchScope.value)
            assertEquals(SearchSort.RELEVANCE, viewModel.searchSort.value)
            assertEquals(SearchDateRange(), viewModel.searchDateRange.value)
        }
    }

    /** 搜索只读取 Room 索引，打开结果也不应在搜索页读取/改写文件。 */
    private object UnusedFiles : FileRepository {
        override val currentLibraryTarget = flowOf<LibraryTarget?>(null)
        override suspend fun selectRootFolder(): FileResult<String> = error("unused")
        override suspend fun readFileContent(fileUri: String): FileResult<String> = error("unused")
        override suspend fun createMarkdownFile(target: LibraryTarget, fileName: String, content: String): FileResult<MarkdownFileMeta> = error("unused")
        override suspend fun saveFileContent(fileUri: String, content: String): FileResult<Unit> = error("unused")
        override suspend fun deleteFile(fileUri: String): FileResult<Unit> = error("unused")
        override suspend fun renameFile(fileUri: String, newName: String): FileResult<String> = error("unused")
        override suspend fun hasSelectedFolder(): Boolean = error("unused")
    }
}
