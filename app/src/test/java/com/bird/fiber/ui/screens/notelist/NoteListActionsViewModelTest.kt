package com.bird.fiber.ui.screens.notelist

import app.cash.turbine.test
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.toTarget
import com.bird.fiber.data.model.FileError
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.data.model.toUserMessage
import com.bird.fiber.data.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListActionsViewModelTest {
    private val files = mockk<FileRepository>()
    private val libraries = mockk<LibraryRepository>()
    private val bus = mockk<EventBus>(relaxed = true)
    private val markdownFileDao = mockk<MarkdownFileDao>()
    private val libraryA = LibraryEntity("a", "工作", "content://test/a", 0)
    private val libraryB = LibraryEntity("b", "生活", "content://test/b", 0)
    private val active = MutableStateFlow<LibraryEntity?>(libraryA)
    private val note = MarkdownFileMeta("content://test/b/note", "笔记", "note.md", 0, 50, "仅预览", libraryId = "b")
    private lateinit var actions: NoteListActionsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { libraries.getActiveLibrary() } returns active
        every { libraries.getAllLibraries() } returns MutableStateFlow(listOf(libraryA, libraryB))
        coEvery { libraries.getLibraryById("b") } returns libraryB
        coEvery { libraries.switchLibrary(any()) } returns Unit
        actions = NoteListActionsViewModel(files, libraries, bus, markdownFileDao)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `复制跨库笔记读取全文且不切库`() = runTest {
        val fullText = "完整内容".repeat(1_000)
        coEvery { files.readFileContent(note.uri) } returns FileResult.Success(fullText)
        actions.events.test {
            actions.copyFile(note)
            assertEquals(NoteListActionEvent.CopyContent(fullText), awaitItem())
        }
        coVerify(exactly = 0) { libraries.switchLibrary(any()) }
    }

    @Test fun `跨库编辑先切到条目所属库再打开原URI`() = runTest {
        actions.events.test {
            actions.openFile(note, true)
            assertEquals(NoteListActionEvent.OpenFile(note.uri, true), awaitItem())
        }
        coVerify(exactly = 1) { libraries.switchLibrary("b") }
    }

    @Test fun `本库查看不重复切库以免触发索引刷新`() = runTest {
        active.value = libraryB
        actions.events.test {
            actions.openFile(note, false)
            assertEquals(NoteListActionEvent.OpenFile(note.uri, false), awaitItem())
        }
        coVerify(exactly = 0) { libraries.switchLibrary(any()) }
    }

    @Test fun `选择记录库只切换保存位置不打开笔记`() = runTest {
        actions.events.test {
            actions.selectRecordingLibrary(libraryB)
            expectNoEvents()
        }
        coVerify(exactly = 1) { libraries.switchLibrary("b") }
        coVerify(exactly = 0) { files.createMarkdownFile(any(), any(), any()) }
    }

    @Test fun `选择当前记录库不重复切库`() = runTest {
        active.value = libraryB
        actions.selectRecordingLibrary(libraryB)
        coVerify(exactly = 0) { libraries.switchLibrary(any()) }
    }

    @Test fun `记录库已被删除时保留当前库并提示`() = runTest {
        coEvery { libraries.getLibraryById("b") } returns null
        actions.events.test {
            actions.selectRecordingLibrary(libraryB)
            assertEquals(NoteListActionEvent.Message("笔记库已不存在，请重新选择"), awaitItem())
        }
        coVerify(exactly = 0) { libraries.switchLibrary(any()) }
    }

    @Test fun `已有置顶时先要求确认且确认后替换`() = runTest {
        val pinned = MarkdownFileDao.PinnedNote("content://test/b/pinned", "原置顶笔记.md")
        coEvery { markdownFileDao.togglePin(note.uri, null) } returns
            MarkdownFileDao.PinToggleResult.ConfirmationRequired(pinned)
        coEvery { markdownFileDao.togglePin(note.uri, pinned.uri) } returns
            MarkdownFileDao.PinToggleResult.Pinned
        actions.events.test {
            actions.togglePin(note)
            assertEquals(NoteListActionsViewModel.PinReplacement(note, pinned), actions.pinReplacement.value)
            expectNoEvents()

            actions.confirmPinReplacement()
            assertEquals(NoteListActionEvent.Message("已置顶「笔记」"), awaitItem())
        }
        assertEquals(null, actions.pinReplacement.value)
        coVerify { markdownFileDao.togglePin(note.uri, null) }
        coVerify { markdownFileDao.togglePin(note.uri, pinned.uri) }
        coVerify(exactly = 0) { files.renameFile(any(), any()) }
        coVerify(exactly = 0) { files.saveFileContent(any(), any()) }
    }

    @Test fun `取消替换确认不修改置顶`() = runTest {
        val pinned = MarkdownFileDao.PinnedNote("content://test/b/pinned", "原置顶笔记.md")
        coEvery { markdownFileDao.togglePin(note.uri, null) } returns
            MarkdownFileDao.PinToggleResult.ConfirmationRequired(pinned)
        actions.togglePin(note)
        actions.dismissPinReplacement()

        assertEquals(null, actions.pinReplacement.value)
        coVerify(exactly = 1) { markdownFileDao.togglePin(note.uri, null) }
        coVerify(exactly = 0) { markdownFileDao.togglePin(note.uri, pinned.uri) }
    }

    @Test fun `切库失败不进入可能使用错误附件目录的编辑器`() = runTest {
        coEvery { libraries.switchLibrary("b") } throws IllegalStateException("切库失败")
        actions.events.test {
            actions.openFile(note, true)
            assertEquals(NoteListActionEvent.Message("切库失败"), awaitItem())
            expectNoEvents()
        }
    }

    @Test fun `删除失败不报成功也不发刷新通知且仍允许重试`() = runTest {
        val error = FileError.PermissionDenied(note.uri)
        coEvery { files.deleteFile(note.uri) } returns FileResult.Error(error) andThen FileResult.Success(Unit)
        actions.events.test {
            actions.deleteFile(note)
            assertEquals(NoteListActionEvent.Message(error.toUserMessage()), awaitItem())
            coVerify(exactly = 0) { bus.emit(any()) }
            actions.deleteFile(note)
            assertEquals(NoteListActionEvent.Message("「笔记」已删除"), awaitItem())
        }
        coVerify(exactly = 1) { bus.emit(AppEvent.FileDeleted(note.uri)) }
        coVerify(exactly = 0) { libraries.switchLibrary(any()) }
    }

    @Test fun `重命名跨库笔记保留目标URI并通知更新后的URI`() = runTest {
        val newUri = "content://test/b/renamed"
        coEvery { files.renameFile(note.uri, "新名称") } returns FileResult.Success(newUri)
        actions.events.test {
            actions.renameFile(note, "新名称")
            assertEquals(NoteListActionEvent.Message("已重命名为「新名称」"), awaitItem())
        }
        coVerify { bus.emit(AppEvent.FileUpdated(newUri)) }
        coVerify(exactly = 0) { libraries.switchLibrary(any()) }
    }

    @Test fun `历史回顾中新建使用确认的库且成功后直接进入编辑`() = runTest {
        coEvery { files.createMarkdownFile(libraryA.toTarget(), "回顾", "") } returns FileResult.Success(note)
        // 对话框打开后激活库变化，仍写入用户打开对话框时看到的工作库。
        active.value = libraryB
        actions.events.test {
            actions.createFile(libraryA, "回顾")
            assertEquals(NoteListActionEvent.OpenFile(note.uri, true), awaitItem())
        }
        coVerify { files.createMarkdownFile(libraryA.toTarget(), "回顾", "") }
        coVerify { bus.emit(AppEvent.FileCreated(note.uri)) }
        coVerify { libraries.switchLibrary("a") }
    }
}
