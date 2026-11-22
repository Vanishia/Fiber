package com.bird.fiber.ui.screens.notelist

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.local.AttachmentRepositoryImpl
import com.bird.fiber.data.local.library.FiberDatabase
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.MarkdownFileEntity
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.LibraryTarget
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.data.repository.FileRepository
import com.bird.fiber.domain.usecase.CreateMarkdownFileUseCase
import com.bird.fiber.domain.usecase.GenerateFileNameUseCase
import com.bird.fiber.ui.screens.quicknote.QuickNoteViewModel
import com.bird.fiber.ui.theme.FiberTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 真实浏览页面与 Room 刷新，文件读写用可观测替身隔离 SAF。 */
class NoteListActionsTest {
    @get:Rule val compose = createComposeRule()
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: FiberDatabase
    private lateinit var files: TestFiles
    private val store = ViewModelStore()
    private val day = LocalDate.of(2024, 2, 29)
    private val noteUri = "content://test/b/note"
    private val pinnedUri = "content://test/b/pinned"
    private var opened: Pair<String, Boolean>? = null

    @Before fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(instrument.targetContext, FiberDatabase::class.java).build()
        db.libraryDao().insertLibrary(LibraryEntity("a", "工作库", "content://test/a", 0, isActive = true))
        db.libraryDao().insertLibrary(LibraryEntity("b", "生活库", "content://test/b", 0))
        db.markdownFileDao().insert(MarkdownFileEntity(
            uri = noteUri, name = "回顾笔记", path = "note.md", size = 100, libraryId = "b",
            lastModified = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            contentPreview = "仅显示预览", contentText = "仅显示预览"
        ))
        db.markdownFileDao().insert(MarkdownFileEntity(
            uri = pinnedUri, name = "原置顶笔记", path = "pinned.md", size = 100, libraryId = "b",
            lastModified = day.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            contentPreview = "原置顶", contentText = "原置顶", pinnedLibraryId = "b"
        ))
        files = TestFiles()
    }

    @After fun tearDown() {
        compose.runOnIdle { store.clear() }
        db.close()
    }

    @Test fun allNotesSupportsMenuAndRecording() { checkActions(null) }
    @Test fun dayNotesSupportsMenuAndRecording() { checkActions(day) }

    private fun checkActions(selectedDay: LocalDate?) {
        val libraries = LibraryRepository(db.libraryDao())
        val bus = EventBus()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                NoteListViewModel::class.java -> NoteListViewModel(
                    db.markdownFileDao(), libraries, SavedStateHandle(mapOf("date" to selectedDay?.toString()))
                )
                NoteListActionsViewModel::class.java -> NoteListActionsViewModel(files, libraries, bus, db.markdownFileDao())
                QuickNoteViewModel::class.java -> QuickNoteViewModel(
                    CreateMarkdownFileUseCase(files, GenerateFileNameUseCase()), bus,
                    AttachmentRepositoryImpl(instrument.targetContext, libraries, db.markdownFileDao()), files
                )
                else -> error("Unexpected view model")
            } as T
        }
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        compose.setContent {
            val provider = ViewModelProvider(owner, factory)
            FiberTheme(dynamicColor = false) {
                NoteListWithActions(
                    viewModel = provider[NoteListViewModel::class.java],
                    actions = provider[NoteListActionsViewModel::class.java],
                    quickNotes = provider[QuickNoteViewModel::class.java],
                    date = selectedDay, onMenuClick = {}, onSearchClick = {}, onAddLibrary = {},
                    onFileClick = { uri, edit -> opened = uri to edit }
                )
            }
        }
        waitForText("回顾笔记")
        compose.onNodeWithText("记录到：工作库").assertIsDisplayed()
        compose.onNodeWithText("记录到：工作库").performClick()
        compose.onNodeWithText("保存到库").assertIsDisplayed()
        compose.onNodeWithText("生活库").performClick()
        waitForText("记录到：生活库")
        assertEquals(null, opened)
        compose.onNodeWithText("记录到：生活库").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("记录到：生活库").assertIsDisplayed()
        compose.onNodeWithText("记录到：生活库").performClick()
        compose.onNodeWithText("工作库").performClick()
        waitForText("记录到：工作库")
        selectedDay?.let { compose.onNodeWithText(it.toString()).assertIsDisplayed() }
        menu("回顾笔记")
        listOf("复制内容", "分享", "置顶", "重命名", "编辑", "删除", "取消").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("置顶").performClick()
        compose.onNodeWithText("替换置顶笔记？").assertIsDisplayed()
        compose.onNodeWithText("「原置顶笔记」已置顶。继续后将改为置顶「回顾笔记」。").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertEquals("b", runBlocking { db.markdownFileDao().getFileByUri(pinnedUri)?.pinnedLibraryId })
        assertEquals(null, runBlocking { db.markdownFileDao().getFileByUri(noteUri)?.pinnedLibraryId })

        menu("回顾笔记")
        compose.onNodeWithText("置顶").performClick()
        compose.onNodeWithText("替换").performClick()
        compose.waitUntil(5_000) { runBlocking { db.markdownFileDao().getFileByUri(noteUri)?.pinnedLibraryId == "b" } }
        assertEquals(null, runBlocking { db.markdownFileDao().getFileByUri(pinnedUri)?.pinnedLibraryId })
        compose.waitForIdle()
        menu("回顾笔记")
        compose.onNodeWithText("取消置顶").assertIsDisplayed()
        compose.onNodeWithText("取消置顶").performClick()
        compose.waitUntil(5_000) { runBlocking { db.markdownFileDao().getFileByUri(noteUri)?.pinnedLibraryId == null } }
        compose.waitForIdle()
        menu("回顾笔记")
        compose.onNodeWithText("复制内容").performClick()
        compose.waitUntil(5_000) { files.readUri == noteUri }
        compose.waitForIdle()
        instrument.runOnMainSync {
            val clipboard = instrument.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(files.fullContent, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        // 复制不改变记录库；编辑才切换到条目库。
        compose.onNodeWithText("记录到：工作库").assertIsDisplayed()
        menu("回顾笔记")
        compose.onNodeWithText("编辑").performClick()
        compose.waitUntil(5_000) { opened == noteUri to true }
        waitForText("记录到：生活库")
        menu("回顾笔记")
        compose.onNodeWithText("重命名").performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("重命名后的笔记")
        compose.onNodeWithText("重命名", useUnmergedTree = true).performClick()
        waitForText("重命名后的笔记")
        assertEquals("b", runBlocking { db.markdownFileDao().getFileByUri(noteUri) }!!.libraryId)

        menu("重命名后的笔记")
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("确认删除").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("重命名后的笔记").assertIsDisplayed()
        menu("重命名后的笔记")
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(5_000) { runBlocking { db.markdownFileDao().getFileByUri(noteUri) } == null }
        compose.waitForIdle()
        compose.onNodeWithText("重命名后的笔记").assertDoesNotExist()

        compose.onNodeWithContentDescription("新建笔记").performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("新建回顾")
        compose.onNodeWithText("创建").performClick()
        compose.waitUntil(5_000) { files.created.size == 1 && opened?.first == "content://test/new/1" }
        compose.runOnIdle {
            assertEquals(LibraryTarget("b", "content://test/b"), files.created.single().first)
            assertEquals(true, opened?.second)
        }
        compose.onNodeWithText("记录到：生活库").performClick()
        compose.onNodeWithText("工作库").performClick()
        waitForText("记录到：工作库")
        compose.onNode(hasSetTextAction()).performTextInput("顺手记录新的想法")
        compose.onNodeWithContentDescription("发送").performClick()
        compose.waitUntil(5_000) { files.created.size == 2 }
        compose.runOnIdle {
            assertEquals("a", files.created.last().first.libraryId)
            assertEquals("顺手记录新的想法", files.created.last().second)
        }
        selectedDay?.let { compose.onNodeWithText(it.toString()).assertIsDisplayed() }
    }

    private fun menu(name: String) {
        compose.onNodeWithText(name).performTouchInput { longClick() }
        compose.onNodeWithText("笔记操作").assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private inner class TestFiles : FileRepository {
        val fullContent = "完整正文，包含预览之外的内容。".repeat(100)
        @Volatile var readUri: String? = null
        val created = java.util.concurrent.CopyOnWriteArrayList<Pair<LibraryTarget, String>>()
        override val currentLibraryTarget = db.libraryDao().getActiveLibrary().map { it?.let { LibraryTarget(it.id, it.folderUri) } }
        override suspend fun readFileContent(fileUri: String): FileResult<String> {
            readUri = fileUri
            return FileResult.Success(fullContent)
        }
        override suspend fun deleteFile(fileUri: String): FileResult<Unit> {
            db.markdownFileDao().delete(fileUri)
            return FileResult.Success(Unit)
        }
        override suspend fun renameFile(fileUri: String, newName: String): FileResult<String> {
            val file = db.markdownFileDao().getFileByUri(fileUri)!!
            db.markdownFileDao().update(file.copy(name = newName))
            return FileResult.Success(fileUri)
        }
        override suspend fun createMarkdownFile(target: LibraryTarget, fileName: String, content: String): FileResult<MarkdownFileMeta> {
            created.add(target to content)
            return FileResult.Success(MarkdownFileMeta("content://test/new/${created.size}", fileName, "$fileName.md", System.currentTimeMillis(), content.length.toLong(), content))
        }
        override suspend fun selectRootFolder(): FileResult<String> = error("unused")
        override suspend fun saveFileContent(fileUri: String, content: String): FileResult<Unit> = error("unused")
        override suspend fun hasSelectedFolder(): Boolean = true
    }
}
