package com.bird.fiber.ui.screens.tags

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.local.*
import com.bird.fiber.data.local.library.*
import com.bird.fiber.data.model.*
import com.bird.fiber.data.repository.FileRepository
import com.bird.fiber.ui.components.TagBackfillOverlay
import com.bird.fiber.ui.navigation.FiberRoute
import com.bird.fiber.ui.screens.editor.*
import com.bird.fiber.ui.screens.notelist.NoteListActionsViewModel
import com.bird.fiber.ui.screens.sidebar.TagList
import com.bird.fiber.ui.theme.FiberTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TagInteractionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expandIsSeparateFromNavigationAndVirtualParentCannotBeDeleted() {
        val child = TagNode("读书/心理", "读书/心理", 0, 1, true, false, emptyList())
        val root = TagNode("读书", "读书", 0, 1, false, false, listOf(child))
        var selected = ""
        var deleted = ""
        compose.setContent { FiberTheme { TagList(listOf(root), false, { selected = it }, { deleted = it }) } }
        compose.onNodeWithText("心理").assertDoesNotExist()
        compose.onNodeWithContentDescription("删除空标签 读书").assertDoesNotExist()
        compose.onNodeWithContentDescription("展开 读书").performClick()
        compose.onNodeWithText("心理").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("读书/心理", selected) }
        compose.onNodeWithContentDescription("删除空标签 读书/心理").performClick()
        compose.runOnIdle { assertEquals("读书/心理", deleted) }
    }

    @Test fun organizingOverlayAllowsMainInterfaceInteraction() {
        var clicks by mutableIntStateOf(0)
        compose.setContent {
            FiberTheme {
                Box(Modifier.fillMaxSize()) {
                    Button(onClick = { clicks++ }) { Text("继续操作 $clicks") }
                    TagBackfillOverlay(TagBackfillState(true, 10, 3, 7), {}, Modifier.align(Alignment.BottomEnd))
                }
            }
        }
        compose.onNodeWithText("标签数据正在整理").assertIsDisplayed()
        compose.onNodeWithText("继续操作 0").performClick()
        compose.onNodeWithText("继续操作 1").assertIsDisplayed()
        compose.onNodeWithText("3 / 10").assertIsDisplayed()
    }

    @Test fun toolbarInsertsAsciiHashRetainsFocusAndSupportsUndo() {
        val session = EditorSession()
        session.reset(TextFieldValue("标签", TextRange(0, 2)))
        var value by mutableStateOf(session.value)
        compose.setContent {
            FiberTheme {
                Column {
                    TextField(value, { session.update(it); value = session.value })
                    EditorToolbar(session.canUndo, session.canRedo, true, false,
                        onUndo = { session.undo(); value = session.value },
                        onRedo = { session.redo(); value = session.value },
                        onFormat = { format -> session.perform { EditorOperations.format(it, format) }; value = session.value },
                        onImage = {})
                }
            }
        }
        compose.onNode(hasSetTextAction()).performClick()
        compose.runOnIdle { value = value.copy(selection = TextRange(0, 2)); session.update(value) }
        compose.onNodeWithContentDescription("标签").performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused().assertTextEquals("#")
        compose.runOnIdle { assertEquals(TextRange(1), value.selection) }
        compose.onNodeWithContentDescription("撤销").performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("标签")
    }

    @Test fun noteCardsShowMutedTagIndicatorsOnlyWhenIndexed() {
        val tagged = MarkdownFileMeta("tagged", "标签笔记", "tagged.md", 10, 0, hasTags = true, isPinned = true)
        val plain = MarkdownFileMeta("plain", "普通笔记", "plain.md", 10, 0)
        var hasTags by mutableStateOf(true)
        compose.setContent {
            FiberTheme {
                Column {
                    com.bird.fiber.ui.screens.filelist.components.FileListItem(
                        file = tagged.copy(hasTags = hasTags), onClick = {}, swipeEnabled = false
                    )
                    com.bird.fiber.ui.screens.filelist.components.WaterfallNoteCard(
                        file = tagged.copy(hasTags = hasTags), onClick = {}
                    )
                    com.bird.fiber.ui.screens.filelist.components.FileListItem(
                        file = plain, onClick = {}, swipeEnabled = false
                    )
                    com.bird.fiber.ui.screens.filelist.components.WaterfallNoteCard(file = plain, onClick = {})
                }
            }
        }
        compose.onAllNodesWithContentDescription("包含标签").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("已置顶").assertCountEquals(2)
        compose.runOnIdle { hasTags = false }
        compose.onAllNodesWithContentDescription("包含标签").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("已置顶").assertCountEquals(2)
    }

    @Test fun encodedRoutePreservesFullUnicodeHierarchy() {
        val path = "读书/心理_android/2026"
        compose.setContent {
            FiberTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") { Button(onClick = { nav.navigate(FiberRoute.tagNotes(path)) }) { Text("打开标签") } }
                    composable(FiberRoute.TAG_NOTES, listOf(navArgument("tagPath") { type = NavType.StringType })) {
                        Text(checkNotNull(it.arguments?.getString("tagPath")))
                    }
                }
            }
        }
        compose.onNodeWithText("打开标签").performClick()
        compose.onNodeWithText(path).assertIsDisplayed()
    }

    @Test fun tagPageShowsLibrariesAndKeepsGlobalScopeAfterOpeningAnotherLibrary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, FiberDatabase::class.java).build()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val models = androidx.lifecycle.ViewModelStore()
        try {
            runBlocking {
                db.libraryDao().insertLibrary(LibraryEntity("a", "工作库", "content://test/a", 0, isActive = true))
                db.libraryDao().insertLibrary(LibraryEntity("b", "生活库", "content://test/b", 0))
                val writer = MarkdownIndexWriter(db.markdownFileDao())
                writer.insert(MarkdownFileEntity("a-note", "工作笔记", "a.md", 10, 10, "a", "工作内容", "#项目/android"))
                writer.insert(MarkdownFileEntity("b-note", "生活笔记", "b.md", 20, 10, "b", "生活内容", "#项目/Android"))
            }
            val backfill = TagBackfillCoordinator(FileIndexer(db.markdownFileDao()), context.contentResolver, testScope)
            val vm = TagNotesViewModel(SavedStateHandle(mapOf("tagPath" to "项目")), TagRepository(db), backfill)
            val actions = NoteListActionsViewModel(UnusedFiles(), LibraryRepository(db.libraryDao()), EventBus(), db.markdownFileDao())
            models.put("tags", vm)
            models.put("actions", actions)
            var opened by mutableStateOf(false)
            compose.setContent {
                FiberTheme {
                    if (opened) Button(onClick = { opened = false }) { Text("返回标签") }
                    else TagNotesScreen({}, { _, _ -> opened = true }, vm, actions)
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("生活笔记").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("工作库").assertIsDisplayed()
            compose.onNodeWithText("生活库").assertIsDisplayed()
            compose.onNodeWithText("2 篇笔记").assertIsDisplayed()
            compose.onNodeWithText("生活笔记").performTouchInput { longClick() }
            compose.onNodeWithText("笔记操作").assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithText("生活笔记").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("返回标签").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("返回标签").assertIsDisplayed()
            runBlocking { assertEquals("b", db.libraryDao().getActiveLibrary().first()?.id) }
            compose.onNodeWithText("返回标签").performClick()
            compose.onNodeWithText("2 篇笔记").assertIsDisplayed()
            compose.onNodeWithText("工作笔记").assertIsDisplayed()
        } finally { models.clear(); testScope.cancel(); db.close() }
    }

    private class UnusedFiles : FileRepository {
        override val currentLibraryTarget = flowOf<LibraryTarget?>(null)
        override suspend fun selectRootFolder(): FileResult<String> = error("unused")
        override suspend fun readFileContent(fileUri: String): FileResult<String> = error("unused")
        override suspend fun createMarkdownFile(target: LibraryTarget, fileName: String, content: String): FileResult<MarkdownFileMeta> = error("unused")
        override suspend fun saveFileContent(fileUri: String, content: String): FileResult<Unit> = error("unused")
        override suspend fun deleteFile(fileUri: String): FileResult<Unit> = error("unused")
        override suspend fun renameFile(fileUri: String, newName: String): FileResult<String> = error("unused")
        override suspend fun hasSelectedFolder(): Boolean = false
    }
}
